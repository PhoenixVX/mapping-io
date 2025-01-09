/*
 * Copyright (c) 2025 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.mappingio.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingUtil;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;
import net.fabricmc.mappingio.adapter.OuterClassNamePropagator;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.intellij.MigrationMapConstants;
import net.fabricmc.mappingio.test.lib.jool.Unchecked;
import net.fabricmc.mappingio.test.visitors.NopMappingVisitor;
import net.fabricmc.mappingio.test.visitors.VisitOrderVerifyingVisitor;

/*
 * After any changes to the "generate" methods, run the "generateTestMappings" Gradle task
 * to update the mapping files located in the resources folder accordingly.
 *
 * Make sure to keep the manual Enigma and SRG changes in the "repeated-elements" directory.
 */
public class TestMappings {
	public static <T extends MappingVisitor> T generateValid(T target) throws IOException {
		MappingVisitor delegate = target instanceof VisitOrderVerifyingVisitor ? target : new VisitOrderVerifyingVisitor(target);

		if (delegate.visitHeader()) {
			delegate.visitNamespaces(MappingUtil.NS_SOURCE_FALLBACK, Arrays.asList(MappingUtil.NS_TARGET_FALLBACK, MappingUtil.NS_TARGET_FALLBACK + "2"));
			delegate.visitMetadata("name", "valid");
			delegate.visitMetadata(MigrationMapConstants.ORDER_KEY, "0");
		}

		if (delegate.visitContent()) {
			int[] dstNs = new int[] { 0, 1 };
			NameGen nameGen = new NameGen();

			if (nameGen.visitClass(delegate, dstNs)) {
				nameGen.visitField(delegate, dstNs);

				if (nameGen.visitMethod(delegate, dstNs)) {
					nameGen.visitMethodArg(delegate, dstNs);
					nameGen.visitMethodVar(delegate, dstNs);
				}
			}

			if (nameGen.visitInnerClass(delegate, 1, dstNs)) {
				nameGen.visitComment(delegate);
				nameGen.visitField(delegate, dstNs);
			}

			nameGen.visitClass(delegate, dstNs);
		}

		if (!delegate.visitEnd()) {
			generateValid(delegate);
		}

		return target;
	}

	public static <T extends MappingVisitor> T generateRepeatedElements(T target, boolean repeatComments, boolean repeatClasses) throws IOException {
		generateValid(new ForwardingMappingVisitor(new VisitOrderVerifyingVisitor(target, true)) {
			private final List<Runnable> replayQueue = new ArrayList<>();

			@Override
			public void visitMetadata(String key, @Nullable String value) throws IOException {
				super.visitMetadata(key, key.equals("name") ? "repeated-elements" : value);
			}

			@Override
			public boolean visitClass(String srcName) throws IOException {
				replayQueue.clear();

				if (repeatClasses) {
					replayQueue.add(Unchecked.runnable(() -> super.visitClass(srcName)));
				}

				return super.visitClass(srcName);
			}

			@Override
			public boolean visitField(String srcName, @Nullable String srcDesc) throws IOException {
				replayQueue.clear();
				replayQueue.add(Unchecked.runnable(() -> super.visitField(srcName, srcDesc)));
				return super.visitField(srcName, srcDesc);
			}

			@Override
			public boolean visitMethod(String srcName, @Nullable String srcDesc) throws IOException {
				replayQueue.clear();
				replayQueue.add(Unchecked.runnable(() -> super.visitMethod(srcName, srcDesc)));
				return super.visitMethod(srcName, srcDesc);
			}

			@Override
			public boolean visitMethodArg(int lvIndex, int argIndex, String srcName) throws IOException {
				replayQueue.clear();
				replayQueue.add(Unchecked.runnable(() -> super.visitMethodArg(lvIndex, argIndex, srcName)));
				return super.visitMethodArg(lvIndex, argIndex, srcName);
			}

			@Override
			public boolean visitMethodVar(int lvIndex, int varIndex, int startOpIdx, int endOpIdx, String srcName) throws IOException {
				replayQueue.clear();
				replayQueue.add(Unchecked.runnable(() -> super.visitMethodVar(lvIndex, varIndex, startOpIdx, endOpIdx, srcName)));
				return super.visitMethodVar(lvIndex, varIndex, startOpIdx, endOpIdx, srcName);
			}

			@Override
			public void visitDstName(MappedElementKind targetKind, int namespace, String name) throws IOException {
				if (targetKind == MappedElementKind.CLASS && !repeatClasses) {
					super.visitDstName(targetKind, namespace, name);
				} else {
					replayQueue.add(Unchecked.runnable(() -> super.visitDstName(targetKind, namespace, name)));
					super.visitDstName(targetKind, namespace, name + "0");
				}
			}

			@Override
			public void visitDstDesc(MappedElementKind targetKind, int namespace, String desc) throws IOException {
				replayQueue.add(Unchecked.runnable(() -> super.visitDstDesc(targetKind, namespace, desc)));
				super.visitDstDesc(targetKind, namespace, desc);
			}

			@Override
			public boolean visitElementContent(MappedElementKind targetKind) throws IOException {
				boolean ret = super.visitElementContent(targetKind);

				if (!replayQueue.isEmpty()) {
					replayQueue.forEach(Runnable::run);

					ret = super.visitElementContent(targetKind);
				}

				return ret;
			}

			@Override
			public void visitComment(MappedElementKind targetKind, String comment) throws IOException {
				if (repeatComments) {
					super.visitComment(targetKind, comment + ".");
				}

				super.visitComment(targetKind, comment);
			}
		});

		return target;
	}

	public static <T extends MappingVisitor> T generateHoles(T target) throws IOException {
		MappingVisitor delegate = target instanceof VisitOrderVerifyingVisitor ? target : new VisitOrderVerifyingVisitor(target);

		if (delegate.visitHeader()) {
			delegate.visitNamespaces(MappingUtil.NS_SOURCE_FALLBACK, Arrays.asList(MappingUtil.NS_TARGET_FALLBACK, MappingUtil.NS_TARGET_FALLBACK + "2"));
		}

		if (delegate.visitContent()) {
			NameGen nameGen = new NameGen();

			// (Inner) Classes
			for (int nestLevel = 0; nestLevel <= 2; nestLevel++) {
				nameGen.visitClass(delegate);
				nameGen.visitClass(delegate);
				nameGen.visitInnerClass(delegate, nestLevel, 0);
				nameGen.visitInnerClass(delegate, nestLevel, 1);

				if (nameGen.visitInnerClass(delegate, nestLevel)) {
					nameGen.visitComment(delegate);
				}

				if (nameGen.visitInnerClass(delegate, nestLevel, 0)) {
					nameGen.visitComment(delegate);
				}

				if (nameGen.visitInnerClass(delegate, nestLevel, 1)) {
					nameGen.visitComment(delegate);
				}
			}

			if (nameGen.visitClass(delegate)) {
				// Fields
				nameGen.visitField(delegate);
				nameGen.visitField(delegate, 0);
				nameGen.visitField(delegate, 1);

				if (nameGen.visitField(delegate)) {
					nameGen.visitComment(delegate);
				}

				if (nameGen.visitField(delegate, 0)) {
					nameGen.visitComment(delegate);
				}

				if (nameGen.visitField(delegate, 1)) {
					nameGen.visitComment(delegate);
				}

				// Methods
				nameGen.visitMethod(delegate);
				nameGen.visitMethod(delegate, 0);
				nameGen.visitMethod(delegate, 1);

				if (nameGen.visitMethod(delegate)) {
					nameGen.visitComment(delegate);
				}

				if (nameGen.visitMethod(delegate, 0)) {
					nameGen.visitComment(delegate);
				}

				if (nameGen.visitMethod(delegate, 1)) {
					nameGen.visitComment(delegate);
				}

				// Method args
				if (nameGen.visitMethod(delegate)) {
					nameGen.visitMethodArg(delegate);
					nameGen.visitMethodArg(delegate, 1);
					nameGen.visitMethodArg(delegate, 0);

					if (nameGen.visitMethodArg(delegate)) {
						nameGen.visitComment(delegate);
					}

					if (nameGen.visitMethodArg(delegate, 0)) {
						nameGen.visitComment(delegate);
					}

					if (nameGen.visitMethodArg(delegate, 1)) {
						nameGen.visitComment(delegate);
					}
				}

				// Method vars
				if (nameGen.visitMethod(delegate)) {
					nameGen.visitMethodVar(delegate);
					nameGen.visitMethodVar(delegate, 1);
					nameGen.visitMethodVar(delegate, 0);

					if (nameGen.visitMethodVar(delegate)) {
						nameGen.visitComment(delegate);
					}

					if (nameGen.visitMethodVar(delegate, 0)) {
						nameGen.visitComment(delegate);
					}

					if (nameGen.visitMethodVar(delegate, 1)) {
						nameGen.visitComment(delegate);
					}
				}
			}
		}

		if (!delegate.visitEnd()) {
			generateHoles(delegate);
		}

		return target;
	}

	public static <T extends MappingVisitor> T generateOuterClassNamePropagation(T target) throws IOException {
		MappingVisitor delegate = target instanceof VisitOrderVerifyingVisitor ? target : new VisitOrderVerifyingVisitor(target);
		String srcNs = MappingUtil.NS_SOURCE_FALLBACK;
		List<String> dstNamespaces = Arrays.asList("dstNs0", "dstNs1", "dstNs2", "dstNs3", "dstNs4", "dstNs5", "dstNs6");

		if (delegate.visitHeader()) {
			delegate.visitNamespaces(srcNs, dstNamespaces);
		}

		if (delegate.visitContent()) {
			if (delegate.visitClass("class_1")) {
				delegate.visitDstName(MappedElementKind.CLASS, 0, "class1Ns0Rename");
				delegate.visitDstName(MappedElementKind.CLASS, 1, "class1Ns1Rename");
				delegate.visitDstName(MappedElementKind.CLASS, 2, "class1Ns2Rename");
				delegate.visitDstName(MappedElementKind.CLASS, 4, "class1Ns4Rename");

				if (delegate.visitElementContent(MappedElementKind.CLASS)) {
					if (delegate.visitField("field_1", "Lclass_1;")) {
						for (int i = 0; i <= 6; i++) {
							delegate.visitDstDesc(MappedElementKind.FIELD, i, "Lclass_1;");
						}

						delegate.visitElementContent(MappedElementKind.FIELD);
					}
				}
			}

			if (delegate.visitClass("class_1$class_2")) {
				delegate.visitDstName(MappedElementKind.CLASS, 2, "class1Ns2Rename$class2Ns2Rename");
				delegate.visitDstName(MappedElementKind.CLASS, 3, "class_1$class2Ns3Rename");
				delegate.visitDstName(MappedElementKind.CLASS, 4, "class_1$class_2");
				delegate.visitDstName(MappedElementKind.CLASS, 5, "class_1$class2Ns5Rename");

				if (delegate.visitElementContent(MappedElementKind.CLASS)) {
					if (delegate.visitField("field_2", "Lclass_1$class_2;")) {
						for (int i = 0; i <= 6; i++) {
							delegate.visitDstDesc(MappedElementKind.FIELD, i, "Lclass_1$class_2;");
						}

						delegate.visitElementContent(MappedElementKind.FIELD);
					}
				}
			}

			if (delegate.visitClass("class_1$class_2$class_3")) {
				delegate.visitDstName(MappedElementKind.CLASS, 5, "class_1$class_2$class3Ns5Rename");
				delegate.visitDstName(MappedElementKind.CLASS, 6, "class_1$class_2$class3Ns6Rename");

				if (delegate.visitElementContent(MappedElementKind.CLASS)) {
					if (delegate.visitField("field_2", "Lclass_1$class_2$class_3;")) {
						for (int i = 0; i <= 6; i++) {
							delegate.visitDstDesc(MappedElementKind.FIELD, i, "Lclass_1$class_2$class_3;");
						}

						delegate.visitElementContent(MappedElementKind.FIELD);
					}
				}
			}
		}

		if (!delegate.visitEnd()) {
			generateOuterClassNamePropagation(delegate);
		}

		return target;
	}

	private static MappingDir register(MappingDir dir) {
		dirs.add(dir);
		dirsByPath.put(dir.path, dir);
		return dir;
	}

	public static Set<MappingDir> values() {
		return Collections.unmodifiableSet(dirs);
	}

	private static final Set<MappingDir> dirs = new HashSet<>();
	private static final Map<Path, MappingDir> dirsByPath = new HashMap<>();

	public static final MappingDir DETECTION = register(new MappingDir(TestUtil.getResource("/detection/")) {
		public <T extends MappingVisitor> T generate(T target) throws IOException {
			throw new UnsupportedOperationException();
		};
	});
	public static final Path MERGING = TestUtil.getResource("/merging/");

	public static class READING {
		public static final Path BASE_DIR = TestUtil.getResource("/reading/");

		public static final MappingDir VALID = register(new MappingDir(BASE_DIR.resolve("valid/")) {
			public <T extends MappingVisitor> T generate(T target) throws IOException {
				return generateValid(target);
			};
		});
		public static final MappingDir HOLES = register(new MappingDir(BASE_DIR.resolve("holes/")) {
			public <T extends MappingVisitor> T generate(T target) throws IOException {
				return generateHoles(target);
			};
		});
		public static final MappingDir REPEATED_ELEMENTS = register(new MappingDir(BASE_DIR.resolve("repeated-elements/")) {
			public <T extends MappingVisitor> T generate(T target) throws IOException {
				return generateRepeatedElements(target, true, true);
			};
		});
	}

	public static class PROPAGATION {
		public static final Path BASE_DIR = TestUtil.getResource("/outer-class-name-propagation/");

		public static final MappingDir UNPROPAGATED = register(new MappingDir(BASE_DIR.resolve("unpropagated/")) {
			public <T extends MappingVisitor> T generate(T target) throws IOException {
				return generateOuterClassNamePropagation(target);
			};
		});
		public static final MappingDir PROPAGATED = register(new MappingDir(BASE_DIR.resolve("propagated/")) {
			public <T extends MappingVisitor> T generate(T target) throws IOException {
				generateOuterClassNamePropagation(new OuterClassNamePropagator(target));
				return target;
			};
		});
		public static final MappingDir PROPAGATED_EXCEPT_REMAPPED_DST = register(new MappingDir(BASE_DIR.resolve("propagated-except-remapped-dst/")) {
			public <T extends MappingVisitor> T generate(T target) throws IOException {
				generateOuterClassNamePropagation(new OuterClassNamePropagator(target, null, false));
				return target;
			};
		});
	}

	static {
		// Force-load classes to ensure all MappingDirs are registered
		READING.BASE_DIR.toString();
		PROPAGATION.BASE_DIR.toString();
	}

	public abstract static class MappingDir {
		private final Path path;
		private boolean supportsGeneration;

		private MappingDir(Path path) {
			this.path = path;

			try {
				generate(new NopMappingVisitor(false));
				supportsGeneration = true;
			} catch (UnsupportedOperationException e) {
				supportsGeneration = false;
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		public Path path() {
			return path;
		}

		public Path pathFor(MappingFormat format) {
			return path.resolve(TestUtil.getFileName(format));
		}

		public boolean isIn(Path path) {
			return this.path.startsWith(path);
		}

		public <T extends MappingVisitor> T read(MappingFormat format, T target) throws IOException {
			MappingReader.read(pathFor(format), format, target);
			return target;
		}

		public boolean supportsGeneration() {
			return supportsGeneration;
		}

		public abstract <T extends MappingVisitor> T generate(T target) throws IOException;
	}
}
