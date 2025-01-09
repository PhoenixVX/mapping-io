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

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Set;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingFlag;
import net.fabricmc.mappingio.MappingUtil;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.format.ColumnFileReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

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
				String lastClassSrcName = null;
				boolean visitClass = false;

				do {
					boolean isMethod = false;

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
					} else if (reader.nextCol(".class_map")) { // class: .class_map <src> <dst>
						String srcName = reader.nextCol();
						if (srcName == null || srcName.isEmpty()) throw new IOException("missing class-name-a in line "+reader.getLineNumber());

						lastClassSrcName = srcName;
						visitClass = visitor.visitClass(srcName);

						if (visitClass) {
							String dstName = reader.nextCol();
							if (dstName == null || dstName.isEmpty()) throw new IOException("missing class-name-b in line "+reader.getLineNumber());

							visitor.visitDstName(MappedElementKind.CLASS, 0, dstName);
							visitClass = visitor.visitElementContent(MappedElementKind.CLASS);
						}
					} else if ((isMethod = reader.nextCol(".method_map")) || reader.nextCol(".field_map")) { // method: .method_map <cls-a><name-a> <desc-a> <name-b> or field: .field_map <cls-a><name-a> <name-b>
						String src = reader.nextCol();
						if (src == null) throw new IOException("missing class-/member-name-a in line "+reader.getLineNumber());

						int srcSepPos = src.lastIndexOf('/');
						if (srcSepPos <= 0 || srcSepPos == src.length() - 1) throw new IOException("invalid class-/member-name-a in line "+reader.getLineNumber());

						String srcDesc = null;
						String dstName;

						if (isMethod) {
							srcDesc = reader.nextCol();
							if (srcDesc == null || srcDesc.isEmpty()) throw new IOException("missing desc-a in line "+reader.getLineNumber());
						}

						dstName = reader.nextCol();
						if (dstName == null) throw new IOException("missing member-name-b in line "+reader.getLineNumber());

						String srcOwner = src.substring(0, srcSepPos);

						if (!srcOwner.equals(lastClassSrcName)) {
							lastClassSrcName = srcOwner;
							visitClass = visitor.visitClass(srcOwner) && visitor.visitElementContent(MappedElementKind.CLASS);
						}

						if (visitClass) {
							String srcName = src.substring(srcSepPos + 1);

							if (!isMethod && visitor.visitField(srcName, srcDesc)) {
								visitor.visitDstName(MappedElementKind.FIELD, 0, dstName);
								visitor.visitElementContent(MappedElementKind.FIELD);
							} else if (isMethod && visitor.visitMethod(srcName, srcDesc)) {
								visitor.visitDstName(MappedElementKind.METHOD, 0, dstName);
								visitor.visitElementContent(MappedElementKind.METHOD);
							}
						}
					} else {
						System.err.println("Unrecognized RGS entry in line "+reader.getLineNumber());
					}
				} while (reader.nextLine(0));
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
