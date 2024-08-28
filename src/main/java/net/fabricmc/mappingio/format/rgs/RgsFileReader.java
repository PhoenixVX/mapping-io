/*
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.mappingio.format.rgs;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingFlag;
import net.fabricmc.mappingio.MappingUtil;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.format.ColumnFileReader;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Set;

public final class RgsFileReader {
	private RgsFileReader() {
	}

	public static void read(Reader reader, MappingVisitor visitor) throws IOException {
		read(reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor);
	}

	public static void read(Reader reader, String sourceNs, String targetNs, MappingVisitor visitor) throws IOException {
		read(new ColumnFileReader(reader, '\t', ' '), sourceNs, targetNs, visitor);
	}

	private static void read(ColumnFileReader reader, String sourceNs, String targetNs, MappingVisitor visitor) throws IOException {
		Set<MappingFlag> flags = visitor.getFlags();
		MappingVisitor parentVisitor = null;
		boolean readerMarked = false;

		if (flags.contains(MappingFlag.NEEDS_ELEMENT_UNIQUENESS)) {
			parentVisitor = visitor;
			visitor = new MemoryMappingTree();
		} else if (flags.contains(MappingFlag.NEEDS_MULTIPLE_PASSES)) {
			reader.mark();
			readerMarked = true;
		}

		for (;;) {
			if (visitor.visitHeader()) {
				visitor.visitNamespaces(sourceNs, Collections.singletonList(targetNs));
			}

			if (visitor.visitContent()) {
				boolean classContentVisitPending = false;

				do {
					if (reader.nextCol(".option")) {
						// TODO: Waiting on the metadata API
					} else if (reader.nextCol(".attribute")) {
						// TODO: Waiting on the metadata API
					} else if (reader.nextCol(".class") || reader.nextCol("!class")) {
						// TODO: This is used to mark classes as deobfuscated/ignored
					} else if (reader.nextCol(".method") || reader.nextCol("!method")) {
						// TODO: This is used to mark methods as deobfuscated/ignored
					} else if (reader.nextCol(".field") || reader.nextCol("!field")) {
						// TODO: This is used to mark fields as deobfuscated/ignored
					} else if (reader.nextCol(".package_map")) {
						// This is used to map obfuscated packages to deobfuscated packages
						String srcName = reader.nextCol();
						if (srcName == null || srcName.isEmpty()) throw new IOException("missing package-name-a in line "+reader.getLineNumber());

						String destName = reader.nextCol();
						if (destName == null || destName.isEmpty()) throw new IOException("missing package-name-b in line "+reader.getLineNumber());
					} else if (reader.nextCol(".repackage_map")) {
						// TODO: This is used to remap packages from one name to another
					} else if (reader.nextCol(".nowarn")) {
						// TODO: This is used to disable warnings outputted by RetroGuard
					} else if (reader.nextCol(".class_map")) {
						String srcName = reader.nextCol();
						if (srcName == null || srcName.isEmpty()) throw new IOException("missing class-name-a in line "+reader.getLineNumber());

						if (classContentVisitPending) {
							visitor.visitElementContent(MappedElementKind.CLASS);
							classContentVisitPending = false;
						}

						if (visitor.visitClass(srcName)) {
							String dstName = reader.nextCol();
							if (dstName == null || dstName.isEmpty()) throw new IOException("missing class-name-b in line "+reader.getLineNumber());

							if (srcName.contains("/")) {
								int srcSepPos = srcName.lastIndexOf('/');
								if (srcSepPos <= 0 || srcSepPos == srcName.length() - 1) throw new IOException("invalid class-package-name-a in line "+reader.getLineNumber());

								// TODO: Remove this
								String packageName = "net/minecraft/src";
								dstName = packageName + "/" + dstName;
							}

							visitor.visitDstName(MappedElementKind.CLASS, 0, dstName);
							classContentVisitPending = true;
						}
					} else if (reader.nextCol(".method_map")) {
						String srcName = reader.nextCol();
						if (srcName == null) throw new IOException("missing class-/name-a in line "+reader.getLineNumber());

						String srcDesc = reader.nextCol();
						if (srcDesc == null || srcDesc.isEmpty()) throw new IOException("missing desc in line "+reader.getLineNumber());

						String dstName = reader.nextCol();
						if (dstName == null) throw new IOException("missing class-/name-b in line "+reader.getLineNumber());

						if (classContentVisitPending) {
							classContentVisitPending = false;
							if (!visitor.visitElementContent(MappedElementKind.CLASS)) continue;
						}

						if (visitor.visitMethod(srcName, srcDesc)) {
							MappedElementKind kind = MappedElementKind.METHOD;
							visitor.visitDstName(kind, 0, dstName);
							visitor.visitElementContent(kind);
						}
					} else if (reader.nextCol(".field_map")) {
						String srcName = reader.nextCol();
						if (srcName == null) throw new IOException("missing class-/name-a in line "+reader.getLineNumber());

						String dstName = reader.nextCol();
						if (dstName == null) throw new IOException("missing class-/name-b in line "+reader.getLineNumber());

						if (classContentVisitPending) {
							classContentVisitPending = false;
							if (!visitor.visitElementContent(MappedElementKind.CLASS)) continue;
						}

						if (visitor.visitField(srcName, null)) {
							MappedElementKind kind = MappedElementKind.FIELD;
							visitor.visitDstName(kind, 0, dstName);
							visitor.visitElementContent(kind);
						}
					} else {
						System.err.println("Unrecognized RGS entry in line "+reader.getLineNumber());
					}
				} while (reader.nextLine(0));

				if (classContentVisitPending) {
					visitor.visitElementContent(MappedElementKind.CLASS);
				}
			}

			if (visitor.visitEnd()) break;

			if (!readerMarked) {
				throw new IllegalStateException("repeated visitation requested without NEEDS_MULTIPLE_PASSES");
			}

			int markIdx = reader.reset();
			assert markIdx == 1;
		}

		if (parentVisitor != null) {
			((MappingTree) visitor).accept(parentVisitor);
		}
	}
}
