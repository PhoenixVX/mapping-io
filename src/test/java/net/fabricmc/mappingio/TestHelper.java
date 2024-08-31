/*
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.mappingio;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.neoforged.srgutils.IMappingFile;
import org.cadixdev.lorenz.io.MappingFormats;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.intellij.MigrationMapConstants;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public final class TestHelper {
	public static Path getResource(String slashPrefixedResourcePath) {
		try {
			return Paths.get(TestHelper.class.getResource(slashPrefixedResourcePath).toURI());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	@Nullable
	public static String getFileName(MappingFormat format) {
		switch (format) {
		case ENIGMA_FILE:
			return "enigma.mappings";
		case ENIGMA_DIR:
			return "enigma-dir";
		case TINY_FILE:
			return "tiny.tiny";
		case TINY_2_FILE:
			return "tinyV2.tiny";
		case SRG_FILE:
			return "srg.srg";
		case XSRG_FILE:
			return "xsrg.xsrg";
		case JAM_FILE:
			return "jam.jam";
		case CSRG_FILE:
			return "csrg.csrg";
		case TSRG_FILE:
			return "tsrg.tsrg";
		case TSRG_2_FILE:
			return "tsrgV2.tsrg";
		case PROGUARD_FILE:
			return "proguard.txt";
		case INTELLIJ_MIGRATION_MAP_FILE:
			return "migration-map.xml";
		case RECAF_SIMPLE_FILE:
			return "recaf-simple.txt";
		case JOBF_FILE:
			return "jobf.jobf";
		default:
			return null;
		}
	}

	@Nullable
	public static org.cadixdev.lorenz.io.MappingFormat toLorenzFormat(MappingFormat format) {
		switch (format) {
		case SRG_FILE:
			return MappingFormats.SRG;
		case XSRG_FILE:
			return MappingFormats.XSRG;
		case CSRG_FILE:
			return MappingFormats.CSRG;
		case TSRG_FILE:
			return MappingFormats.TSRG;
		case ENIGMA_FILE:
			return MappingFormats.byId("enigma");
		case JAM_FILE:
			return MappingFormats.byId("jam");
		case TINY_FILE:
		case TINY_2_FILE:
		case ENIGMA_DIR:
		case TSRG_2_FILE:
		case PROGUARD_FILE:
		case INTELLIJ_MIGRATION_MAP_FILE:
		case RECAF_SIMPLE_FILE:
		case JOBF_FILE:
			return null;
		default:
			throw new IllegalArgumentException("Unknown format: " + format);
		}
	}

	@Nullable
	public static IMappingFile.Format toSrgUtilsFormat(MappingFormat format) {
		switch (format) {
		case TINY_FILE:
			return IMappingFile.Format.TINY1;
		case TINY_2_FILE:
			return IMappingFile.Format.TINY;
		case SRG_FILE:
			return IMappingFile.Format.SRG;
		case XSRG_FILE:
			return IMappingFile.Format.XSRG;
		case CSRG_FILE:
			return IMappingFile.Format.CSRG;
		case TSRG_FILE:
			return IMappingFile.Format.TSRG;
		case TSRG_2_FILE:
			return IMappingFile.Format.TSRG2;
		case PROGUARD_FILE:
			return IMappingFile.Format.PG;
		case ENIGMA_FILE:
		case ENIGMA_DIR:
		case JAM_FILE:
		case INTELLIJ_MIGRATION_MAP_FILE:
		case RECAF_SIMPLE_FILE:
		case JOBF_FILE:
			return null;
		default:
			throw new IllegalArgumentException("Unknown format: " + format);
		}
	}

	public static Path writeToDir(MappingTreeView tree, Path dir, MappingFormat format) throws IOException {
		Path path = dir.resolve(getFileName(format));
		tree.accept(MappingWriter.create(path, format));
		return path;
	}

	// Has to be kept in sync with /resources/read/valid/* test mappings!
	public static MemoryMappingTree createTestTree() {
		MemoryMappingTree tree = new MemoryMappingTree();
		tree.visitNamespaces(MappingUtil.NS_SOURCE_FALLBACK, Arrays.asList(MappingUtil.NS_TARGET_FALLBACK, MappingUtil.NS_TARGET_FALLBACK + "2"));
		tree.visitMetadata("name", "valid");
		tree.visitMetadata(MigrationMapConstants.ORDER_KEY, "0");
		int[] dstNs = new int[] { 0, 1 };
		nameGen.reset();

		visitClass(tree, dstNs);
		visitField(tree, dstNs);
		visitMethod(tree, dstNs);
		visitMethodArg(tree, dstNs);
		visitMethodVar(tree, dstNs);
		visitInnerClass(tree, 1, dstNs);
		visitComment(tree);
		visitField(tree, dstNs);
		visitClass(tree, dstNs);

		return tree;
	}

	// Has to be kept in sync with /resources/read/valid-with-holes/* test mappings!
	public static MemoryMappingTree createTestTreeWithHoles() {
		MemoryMappingTree tree = new MemoryMappingTree();
		tree.visitNamespaces(MappingUtil.NS_SOURCE_FALLBACK, Arrays.asList(MappingUtil.NS_TARGET_FALLBACK, MappingUtil.NS_TARGET_FALLBACK + "2"));
		nameGen.reset();

		// (Inner) Classes
		for (int nestLevel = 0; nestLevel <= 2; nestLevel++) {
			visitClass(tree);
			visitInnerClass(tree, nestLevel, 0);
			visitInnerClass(tree, nestLevel, 1);

			visitInnerClass(tree, nestLevel);
			visitComment(tree);

			visitInnerClass(tree, nestLevel, 0);
			visitComment(tree);

			visitInnerClass(tree, nestLevel, 1);
			visitComment(tree);
		}

		// Fields
		visitClass(tree);
		visitField(tree);
		visitField(tree, 0);
		visitField(tree, 1);

		visitField(tree);
		visitComment(tree);

		visitField(tree, 0);
		visitComment(tree);

		visitField(tree, 1);
		visitComment(tree);

		// Methods
		visitMethod(tree);
		visitMethod(tree, 0);
		visitMethod(tree, 1);

		visitMethod(tree);
		visitComment(tree);

		visitMethod(tree, 0);
		visitComment(tree);

		visitMethod(tree, 1);
		visitComment(tree);

		// Method args
		visitMethod(tree);
		visitMethodArg(tree);
		visitMethodArg(tree, 1);
		visitMethodArg(tree, 0);

		visitMethodArg(tree);
		visitComment(tree);

		visitMethodArg(tree, 0);
		visitComment(tree);

		visitMethodArg(tree, 1);
		visitComment(tree);

		// Method vars
		visitMethod(tree);
		visitMethodVar(tree);
		visitMethodVar(tree, 1);
		visitMethodVar(tree, 0);

		visitMethodVar(tree);
		visitComment(tree);

		visitMethodVar(tree, 0);
		visitComment(tree);

		visitMethodVar(tree, 1);
		visitComment(tree);

		return tree;
	}

	private static void visitClass(MemoryMappingTree tree, int... dstNs) {
		visitInnerClass(tree, 0, dstNs);
	}

	private static void visitInnerClass(MemoryMappingTree tree, int nestLevel, int... dstNs) {
		tree.visitClass(nestLevel <= 0 ? nameGen.src(clsKind) : nameGen.srcInnerCls(nestLevel));

		for (int ns : dstNs) {
			tree.visitDstName(clsKind, ns, nameGen.dst(clsKind, ns));
		}
	}

	private static void visitField(MemoryMappingTree tree, int... dstNs) {
		tree.visitField(nameGen.src(fldKind), nameGen.desc(fldKind));

		for (int ns : dstNs) {
			tree.visitDstName(fldKind, ns, nameGen.dst(fldKind, ns));
		}
	}

	private static void visitMethod(MemoryMappingTree tree, int... dstNs) {
		tree.visitMethod(nameGen.src(mthKind), nameGen.desc(mthKind));

		for (int ns : dstNs) {
			tree.visitDstName(mthKind, ns, nameGen.dst(mthKind, ns));
		}
	}

	private static void visitMethodArg(MemoryMappingTree tree, int... dstNs) {
		tree.visitMethodArg(nameGen.getCounter().getAndIncrement(), nameGen.getCounter().getAndIncrement(), nameGen.src(argKind));

		for (int ns : dstNs) {
			tree.visitDstName(argKind, ns, nameGen.dst(argKind, ns));
		}
	}

	private static void visitMethodVar(MemoryMappingTree tree, int... dstNs) {
		tree.visitMethodVar(nameGen.getCounter().get(), nameGen.getCounter().get(),
				nameGen.getCounter().getAndIncrement(), nameGen.getCounter().getAndIncrement(), nameGen.src(varKind));

		for (int ns : dstNs) {
			tree.visitDstName(varKind, ns, nameGen.dst(varKind, ns));
		}
	}

	private static void visitComment(MemoryMappingTree tree) {
		tree.visitComment(nameGen.lastKind.get(), comment);
	}

	private static class NameGen {
		public void reset() {
			lastKind.remove();
			innerClassNestLevel.remove();
			clsNum.get().set(0);
			fldNum.get().set(0);
			mthNum.get().set(0);
			argNum.get().set(0);
			varNum.get().set(0);
			nsNum.get().set(0);
			counter.get().set(0);
		}

		private void resetNsNum() {
			nsNum.get().set(0);
		}

		public String src(MappedElementKind kind) {
			resetNsNum();
			lastKind.set(kind);
			innerClassNestLevel.set(0);

			if (kind == MappedElementKind.CLASS) {
				outerClassHasDst.set(false);
			}

			return getPrefix(kind) + "_" + getCounter(kind).incrementAndGet();
		}

		public String srcInnerCls(/* >=1 */ int nestLevel) {
			if (innerClassNestLevel.get() == 0) clsNum.get().decrementAndGet();
			boolean hasDst = outerClassHasDst.get();
			StringBuilder sb = new StringBuilder(src(clsKind));

			for (int i = 0; i < nestLevel; i++) {
				sb.append('$');
				sb.append(src(clsKind));
			}

			outerClassHasDst.set(hasDst);
			innerClassNestLevel.set(nestLevel);
			return sb.toString();
		}

		public String dst(MappedElementKind kind) {
			return dst(kind, nsNum.get().getAndIncrement());
		}

		public String dst(MappedElementKind kind, int ns) {
			if (lastKind != null && lastKind.get() != kind) {
				throw new UnsupportedOperationException();
			}

			if (nsNum.get().get() < ns) nsNum.get().set(ns + 1);

			if (innerClassNestLevel.get().intValue() == 0) {
				outerClassHasDst.set(true);
				return getPrefix(kind) + getCounter(kind).get() + "Ns" + ns + "Rename";
			}

			boolean hasDst = outerClassHasDst.get();
			int nestLevel = innerClassNestLevel.get();
			innerClassNestLevel.set(0);
			StringBuilder sb = new StringBuilder(dst(kind, ns));

			for (int i = nestLevel - 1; i >= 0; i--) {
				sb.insert(0, '$');
				clsNum.get().decrementAndGet();
				if (!hasDst) clsNum.get().decrementAndGet();
				sb.insert(0, hasDst ? dst(clsKind) : src(kind));
			}

			outerClassHasDst.set(hasDst);
			innerClassNestLevel.set(nestLevel);
			clsNum.get().addAndGet(nestLevel);
			return sb.toString();
		}

		public String desc(MappedElementKind kind) {
			switch (kind) {
			case FIELD:
				return fldDescs.get((fldNum.get().get() - 1) % fldDescs.size());
			case METHOD:
				return mthDescs.get((mthNum.get().get() - 1) % mthDescs.size());
			default:
				throw new IllegalArgumentException("Invalid kind: " + kind);
			}
		}

		public AtomicInteger getCounter() {
			return counter.get();
		}

		private AtomicInteger getCounter(MappedElementKind kind) {
			switch (kind) {
			case CLASS:
				return clsNum.get();
			case FIELD:
				return fldNum.get();
			case METHOD:
				return mthNum.get();
			case METHOD_ARG:
				return argNum.get();
			case METHOD_VAR:
				return varNum.get();
			default:
				throw new IllegalArgumentException("Unknown kind: " + kind);
			}
		}

		private String getPrefix(MappedElementKind kind) {
			switch (kind) {
			case CLASS:
				return clsPrefix;
			case FIELD:
				return fldPrefix;
			case METHOD:
				return mthPrefix;
			case METHOD_ARG:
				return argPrefix;
			case METHOD_VAR:
				return varPrefix;
			default:
				throw new IllegalArgumentException("Unknown kind: " + kind);
			}
		}

		private static final String clsPrefix = "class";
		private static final String fldPrefix = "field";
		private static final String mthPrefix = "method";
		private static final String argPrefix = "param";
		private static final String varPrefix = "var";
		private ThreadLocal<MappedElementKind> lastKind = ThreadLocal.withInitial(() -> null);
		private ThreadLocal<Boolean> outerClassHasDst = ThreadLocal.withInitial(() -> false);
		private ThreadLocal<Integer> innerClassNestLevel = ThreadLocal.withInitial(() -> 0);
		private ThreadLocal<AtomicInteger> clsNum = ThreadLocal.withInitial(() -> new AtomicInteger());
		private ThreadLocal<AtomicInteger> fldNum = ThreadLocal.withInitial(() -> new AtomicInteger());
		private ThreadLocal<AtomicInteger> mthNum = ThreadLocal.withInitial(() -> new AtomicInteger());
		private ThreadLocal<AtomicInteger> argNum = ThreadLocal.withInitial(() -> new AtomicInteger());
		private ThreadLocal<AtomicInteger> varNum = ThreadLocal.withInitial(() -> new AtomicInteger());
		private ThreadLocal<AtomicInteger> nsNum = ThreadLocal.withInitial(() -> new AtomicInteger());
		private ThreadLocal<AtomicInteger> counter = ThreadLocal.withInitial(() -> new AtomicInteger());
	}

	public static class MappingDirs {
		@Nullable
		public static MemoryMappingTree getCorrespondingTree(Path dir) {
			if (dir.equals(VALID)) return createTestTree();
			if (dir.equals(VALID_WITH_HOLES)) return createTestTreeWithHoles();
			return null;
		}

		public static final Path DETECTION = getResource("/detection/");
		public static final Path VALID = getResource("/read/valid/");
		public static final Path VALID_WITH_HOLES = getResource("/read/valid-with-holes/");
		public static final Path REPEATED_ELEMENTS = getResource("/read/repeated-elements/");
	}

	private static final List<String> fldDescs = Arrays.asList("I", "Lcls;", "Lpkg/cls;", "[I");
	private static final List<String> mthDescs = Arrays.asList("()I", "(I)V", "(Lcls;)Lcls;", "(ILcls;)Lpkg/cls;", "(Lcls;[I)[[B");
	private static final String comment = "This is a comment";
	private static final NameGen nameGen = new NameGen();
	private static final MappedElementKind clsKind = MappedElementKind.CLASS;
	private static final MappedElementKind fldKind = MappedElementKind.FIELD;
	private static final MappedElementKind mthKind = MappedElementKind.METHOD;
	private static final MappedElementKind argKind = MappedElementKind.METHOD_ARG;
	private static final MappedElementKind varKind = MappedElementKind.METHOD_VAR;
}
