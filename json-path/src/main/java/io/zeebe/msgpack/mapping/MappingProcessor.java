/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.msgpack.mapping;

import io.zeebe.msgpack.spec.MsgPackFormat;
import io.zeebe.msgpack.spec.MsgPackType;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Represents an processor, which executes/process the given mapping on the given message pack
 * documents. Uses message pack tree data structure to rewrite/merge the message pack document.
 *
 * <p>There exist two methods to process the mappings, {@link #merge(DirectBuffer, DirectBuffer,
 * Mapping...)} and {@link #extract(DirectBuffer, Mapping...)}. The first one merges two documents
 * with the help of the given mapping into one. The second method extracts content of the given
 * document, with help of the given mapping and writes the result into a buffer. The result is
 * stored into a buffer, which is available via {@link #getResultBuffer()}.
 *
 * <p>On merge the target document will be as first indexed with help of the {@link
 * MsgPackDocumentIndexer}. The indexer construct's a message pack tree object ({@link
 * MsgPackTree}), which corresponds to the given target document. To extract content and merge (add
 * or replace content) into the already create message pack tree, the {@link
 * MsgPackDocumentExtractor} is used.
 *
 * <p>On the extract method only the {@link MsgPackDocumentExtractor} is used to extract content
 * from the given source document and store it into a message pack tree object.
 *
 * <p>Afterwards (on merge or extract) the message pack document, which corresponds to the
 * constructed message pack tree object, is written with help of the {@link
 * MsgPackDocumentTreeWriter} to a result buffer.
 *
 * <p>The result is available via {@link #getResultBuffer()}.
 *
 * <p>Example:
 *
 * <pre>
 * {@code Given source document:           Given target document:
 *  {                                {
 *     "sourceObject":{                 "targetObject":{
 *         "foo":"bar"                      "value1":2
 *     },                               },
 *     "value1":1                       "value1":3
 *  }                                }
 * }
 * </pre>
 *
 * Mappings:
 *
 * <pre>{@code
 * $.sourceObject -> $.targetObject.value1
 * $.value1 -> $.newValue1
 * }</pre>
 *
 * Result on {@link #merge(DirectBuffer, DirectBuffer, Mapping...)}:
 *
 * <pre>{@code
 * {
 *     "targetObject":{
 *         "value1":{
 *             "foo":"bar"
 *         }
 *     },
 *     "value1":3,
 *     "newValue1":1
 * }
 * }</pre>
 *
 * <p>On merge: targetObject.value1 is overwritten, newValue1 is created and value1 is kept.
 *
 * <p>Result on {@link #extract(DirectBuffer, Mapping...)}:
 *
 * <pre>{@code
 * {
 *     "targetObject":{
 *         "value1":{
 *             "foo":"bar"
 *         }
 *     },
 *     "newValue1":1
 * }
 * }</pre>
 *
 * <p>On extract: targetObject.value1 and newValue is created (renamed), since it does not exist
 * before. The value1 is not known in the extracting context.
 */
public class MappingProcessor {
  /** The maximum JSON key length. */
  public static final int MAX_JSON_KEY_LEN = 256;

  /**
   * The message for the exception, which is thrown if the resulting document is not a map (json
   * object).
   */
  public static final String EXCEPTION_MSG_RESULTING_DOCUMENT_IS_NOT_OF_TYPE_MAP =
      "Processing failed, since mapping will result in a non map object (json object).";

  /** The message for the exception which is thrown if the mapping is either null or empty. */
  public static final String EXCEPTION_MSG_MAPPING_NULL_NOR_EMPTY =
      "Mapping must be neither null nor empty!";

  protected final MsgPackDocumentIndexer documentIndexer;
  protected final MsgPackDocumentExtractor documentExtractor;
  protected final MsgPackDocumentTreeWriter treeWriter;
  private final MsgPackDocumentIndexer sourceDocumentIndexer;

  public MappingProcessor(int initialDocumentSize) {
    this.documentIndexer = new MsgPackDocumentIndexer();
    this.sourceDocumentIndexer = new MsgPackDocumentIndexer();
    this.documentExtractor = new MsgPackDocumentExtractor();
    this.treeWriter = new MsgPackDocumentTreeWriter(initialDocumentSize);
  }

  /**
   * This method will merge, with help of the given mappings, a source document into a given target
   * document. The result is after the merging available in the resultBuffer, which can be accessed
   * with {@link #getResultBuffer()}.
   *
   * <p>The target is used to determine which content should be available in the result, with help
   * of the mapping the target can be modified. Objects can be added or replaced.
   *
   * <p>If no mappings are given an top-level merge will be executed.
   *
   * @param sourceDocument the document which is used as source of the mapping
   * @param targetDocument the targetPayload which should be merged with the source document with
   *     help of the mappings
   * @param mappings zero or more mappings, which should be executed
   * @return the resulting length of the message pack
   */
  public int merge(DirectBuffer sourceDocument, DirectBuffer targetDocument, Mapping... mappings) {
    if (targetDocument == null) {
      throw new IllegalArgumentException("Target document must not be null!");
    }

    if (targetDocument.capacity() > 0) {
      ensureSourceDocumentIsNotNull(sourceDocument);

      documentIndexer.wrap(targetDocument);
      final MsgPackTree targetTree = documentIndexer.index();

      final MsgPackTree treeToWrite;
      if (mappings == null || mappings.length == 0) {
        sourceDocumentIndexer.wrap(sourceDocument);
        final MsgPackTree sourceTree = sourceDocumentIndexer.index();
        targetTree.merge(sourceTree);

        treeToWrite = targetTree;
      } else {
        documentExtractor.wrap(targetTree, sourceDocument);
        treeToWrite = documentExtractor.extract(mappings);
      }

      return writeMsgPackTree(treeToWrite);
    } else {
      return extract(sourceDocument, mappings);
    }
  }

  /**
   * This method will extract, with help of the given mappings, a message pack document. After
   * processing the mappings the result is available in the resultBuffer, which can be accessed with
   * {@link #getResultBuffer()}.
   *
   * @param sourceDocument the document which is used as source of the mapping
   * @param mappings one or more mappings, which should be executed
   * @return the resulting length of the message pack
   */
  public int extract(DirectBuffer sourceDocument, Mapping... mappings) {
    ensureSourceDocumentIsNotNull(sourceDocument);
    final MsgPackTree treeToWrite;
    if (mappings == null || mappings.length == 0) {
      sourceDocumentIndexer.wrap(sourceDocument);
      treeToWrite = sourceDocumentIndexer.index();
    } else {
      documentExtractor.wrap(sourceDocument);
      treeToWrite = documentExtractor.extract(mappings);
    }

    return writeMsgPackTree(treeToWrite);
  }

  /**
   * Ensures if the given source document is not null.
   *
   * @param sourceDocument the source document which should not be null
   */
  private void ensureSourceDocumentIsNotNull(DirectBuffer sourceDocument) {
    if (sourceDocument == null) {
      throw new IllegalArgumentException("Source document must not be null!");
    }
  }

  private void ensureDocumentIsAMsgPackMap(DirectBuffer document, String exceptionMsg) {
    final byte b = document.getByte(0);
    final MsgPackFormat format = MsgPackFormat.valueOf(b);
    if (format.getType() != MsgPackType.MAP && format.getType() != MsgPackType.NIL) {
      throw new MappingException(exceptionMsg);
    }
  }

  private int writeMsgPackTree(MsgPackTree msgPackTree) {
    try {
      final int resultLen = treeWriter.write(msgPackTree);
      ensureDocumentIsAMsgPackMap(
          getResultBuffer(), EXCEPTION_MSG_RESULTING_DOCUMENT_IS_NOT_OF_TYPE_MAP);
      return resultLen;
    } finally {
      clear();
    }
  }

  /**
   * The result buffer, which contains the message pack document after processing the mappings.
   *
   * @return the result buffer
   */
  public MutableDirectBuffer getResultBuffer() {
    return treeWriter.getResult();
  }

  /** Tidy up the internal data structure. */
  private void clear() {
    documentIndexer.clear();
    documentExtractor.clear();
    sourceDocumentIndexer.clear();
  }
}
