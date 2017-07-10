/**
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

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import io.zeebe.msgpack.spec.MsgPackFormat;
import io.zeebe.msgpack.spec.MsgPackType;

/**
 * <p>
 * Represents an processor, which executes/process the given mapping on the given message pack documents.
 * Uses message pack tree data structure to rewrite/merge the message pack document.
 * </p>
 *
 * <p>
 * There exist two methods to process the mappings, {@link #merge(DirectBuffer, DirectBuffer, Mapping...)} and
 * {@link #extract(DirectBuffer, Mapping...)}.
 * The first one merges two documents with the help of the given mapping into one. The second method extracts
 * content of the given document, with help of the given mapping and writes the result into a buffer.
 * The result is stored into a buffer, which is available via {@link #getResultBuffer()}.
 * </p>
 *
 * <p>
 * On merge the target document will be as first indexed with help of the {@link MsgPackDocumentIndexer}.
 * The indexer construct's a message pack tree object ({@link MsgPackTree}), which corresponds to the given target document.
 * To extract content and merge (add or replace content) into the already create message pack tree,
 * the {@link MsgPackDocumentExtractor} is used.
 * </p>
 *
 * <p>
 * On the extract method only the {@link MsgPackDocumentExtractor} is used to extract content from the given source document
 * and store it into a message pack tree object.
 * </p>
 *
 * <p>
 * Afterwards (on merge or extract) the message pack document, which corresponds to the
 * constructed message pack tree object, is written with help of the {@link MsgPackDocumentTreeWriter} to a result buffer.
 * </p>
 *
 * <p>
 * The result is available via {@link #getResultBuffer()}.
 * </p>
 *
 *<p>
 * Example:
 * <pre>
 *{@code Given source document:           Given target document:
 *  {                                {
 *     "sourceObject":{                 "targetObject":{
 *         "foo":"bar"                      "value1":2
 *     },                               },
 *     "value1":1                       "value1":3
 *  }                                }
 *}
 * </pre>
 *
 * Mappings:
 * <pre>
 * {@code
 *  $.sourceObject -> $.targetObject.value1
 *  $.value1 -> $.newValue1
 * }
 * </pre>
 * Result on {@link #merge(DirectBuffer, DirectBuffer, Mapping...)}:
 * <pre>
 * {@code
 * {
 *     "targetObject":{
 *         "value1":{
 *             "foo":"bar"
 *         }
 *     },
 *     "value1":3,
 *     "newValue1":1
 * }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * On merge:
 * targetObject.value1 is overwritten, newValue1 is created and value1 is kept.
 *
 * Result on {@link #extract(DirectBuffer, Mapping...)}:
 * <pre>
 *{@code
 * {
 *     "targetObject":{
 *         "value1":{
 *             "foo":"bar"
 *         }
 *     },
 *     "newValue1":1
 * }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * On extract:
 * targetObject.value1 and newValue is created (renamed), since it does not exist before.
 * The value1 is not known in the extracting context.
 * </p>
 */
public class MappingProcessor
{
    /**
     * The maximum JSON key length.
     */
    public static final int MAX_JSON_KEY_LEN = 256;

    /**
     * The message for the exception, which is thrown if the resulting document is not a map (json object).
     */
    public static final String EXCEPTION_MSG_RESULTING_DOCUMENT_IS_NOT_OF_TYPE_MAP = "Processing failed, since mapping will result in a non map object (json object).";

    /**
     * The message for the exception which is thrown if the mapping is either null or empty.
     */
    public static final String EXCEPTION_MSG_MAPPING_NULL_NOR_EMPTY = "Mapping must be neither null nor empty!";

    protected final MsgPackDocumentIndexer documentIndexer;
    protected final MsgPackDocumentExtractor documentExtractor;
    protected final MsgPackDocumentTreeWriter treeWriter;

    public MappingProcessor(int initialDocumentSize)
    {
        this.documentIndexer = new MsgPackDocumentIndexer();
        this.documentExtractor = new MsgPackDocumentExtractor();
        this.treeWriter = new MsgPackDocumentTreeWriter(initialDocumentSize);
    }

    /**
     * This method will merge, with help of the given mappings, a source document into a given target document.
     * The result is after the merging available in the resultBuffer, which can be accessed with {@link #getResultBuffer()}.
     * <p>
     * The target is used to determine which content should be available in the result, with help of the mapping
     * the target can be modified. Objects can be added or replaced.
     *
     * @param sourceDocument the document which is used as source of the mapping
     * @param targetDocument the targetPayload which should be merged with the source document with help of the mappings
     * @param mappings      one or more mappings, which should be executed
     * @return the resulting length of the message pack
     */
    public int merge(DirectBuffer sourceDocument, DirectBuffer targetDocument, Mapping... mappings)
    {
        if (targetDocument == null)
        {
            throw new IllegalArgumentException("Target document must not be null!");
        }

        if (targetDocument.capacity() > 1)
        {
            ensureValidParameter(sourceDocument, mappings);

            documentIndexer.wrap(targetDocument);
            final MsgPackTree targetTree = documentIndexer.index();

            documentExtractor.wrap(targetTree, sourceDocument);
            final MsgPackTree mergedDocumentTree = documentExtractor.extract(mappings);

            return writeMsgPackTree(mergedDocumentTree);
        }
        else
        {
            return extract(sourceDocument, mappings);
        }
    }

    /**
     * This method will extract, with help of the given mappings, a message pack document.
     * After processing the mappings the result is available in the resultBuffer,
     * which can be accessed with {@link #getResultBuffer()}.
     *
     * @param sourceDocument the document which is used as source of the mapping
     * @param mappings      one or more mappings, which should be executed
     * @return the resulting length of the message pack
     */
    public int extract(DirectBuffer sourceDocument, Mapping... mappings)
    {
        ensureValidParameter(sourceDocument, mappings);

        documentExtractor.wrap(sourceDocument);
        final MsgPackTree extractedDocumentTree = documentExtractor.extract(mappings);

        return writeMsgPackTree(extractedDocumentTree);
    }

    /**
     * Ensures if the given parameters are valid.
     *
     * @param sourceDocument the source document which should not be null
     * @param mappings the mappings which must not be neither null nor empty
     *                 is should also not more mappings than one if an root mapping exist
     */
    private void ensureValidParameter(DirectBuffer sourceDocument, Mapping[] mappings)
    {
        if (sourceDocument == null)
        {
            throw new IllegalArgumentException("Source document must not be null!");
        }

        if (mappings == null || mappings.length == 0)
        {
            throw new IllegalArgumentException(EXCEPTION_MSG_MAPPING_NULL_NOR_EMPTY);
        }
    }

    private void ensureDocumentIsAMsgPackMap(DirectBuffer document, String exceptionMsg)
    {
        final byte b = document.getByte(0);
        final MsgPackFormat format = MsgPackFormat.valueOf(b);
        if (format.getType() != MsgPackType.MAP)
        {
            throw new MappingException(exceptionMsg);
        }
    }

    private int writeMsgPackTree(MsgPackTree msgPackTree)
    {
        try
        {
            final int resultLen = treeWriter.write(msgPackTree);
            ensureDocumentIsAMsgPackMap(getResultBuffer(), EXCEPTION_MSG_RESULTING_DOCUMENT_IS_NOT_OF_TYPE_MAP);
            return resultLen;
        }
        finally
        {
            clear();
        }
    }

    /**
     * The result buffer, which contains the message pack document after processing the mappings.
     *
     * @return the result buffer
     */
    public MutableDirectBuffer getResultBuffer()
    {
        return treeWriter.getResult();
    }

    /**
     * Tidy up the internal data structure.
     */
    private void clear()
    {
        documentIndexer.clear();
        documentExtractor.clear();
    }
}
