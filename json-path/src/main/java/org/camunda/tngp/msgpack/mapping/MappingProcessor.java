package org.camunda.tngp.msgpack.mapping;

import java.util.Arrays;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Represents an processor, which executes/process the given mapping on the given message pack documents.
 * Uses message pack tree data structure to rewrite/merge the message pack document.
 * <p>
 *
 * There exist two methods to process the mappings, {@link #merge(DirectBuffer, DirectBuffer, Mapping...)} and
 * {@link #extract(DirectBuffer, Mapping...)}.
 * The first one merges two documents with the help of the given mapping into one. The second method extracts
 * content of the given document, with help of the given mapping and writes the result into a buffer.
 * The result is stored into a buffer, which is available via {@link #getResultBuffer()}.
 *
 * On merge the target document will be as first indexed with help of the {@link MsgPackDocumentIndexer}.
 * The indexer construct's a message pack tree object ({@link MsgPackTree}), which corresponds to the given target document.
 * To extract content and merge (add or replace content) into the already create message pack tree,
 * the {@link MsgPackDocumentExtractor} is used.
 *
 * On the extract method only the {@link MsgPackDocumentExtractor} is used to extract content from the given source document
 * and store it into a message pack tree object.
 *
 * Afterwards (on merge or extract) the message pack document, which corresponds to the
 * constructed message pack tree object, is written with help of the {@link MsgPackDocumentTreeWriter} to a result buffer.
 *
 * The result is available via {@link #getResultBuffer()}.
 *
 *
 * Example:
 * Given source document:           Given target document:
 * {                                {
 *     "sourceObject":{                 "targetObject":{
 *         "foo":"bar"                      "value1":2
 *     },                               },
 *     "value1":1                       "value1":3
 * }                                }
 * Mappings:
 *  $.sourceObject -> $.targetObject.value1
 *  $.value1 -> $.newValue1
 *
 * Result on merge(sourceDocument, targetDocument, mappings):
 *
 * {
 *     "targetObject":{
 *         "value1":{
 *             "foo":"bar"
 *         }
 *     },
 *     "value1":3,
 *     "newValue1":1
 * }
 *
 * On merge:
 * targetObject.value1 is overwritten, newValue1 is created and value1 is kept.
 *
 * Result on extract(sourceDocument, mappings):
 *
 * {
 *     "targetObject":{
 *         "value1":{
 *             "foo":"bar"
 *         }
 *     },
 *     "newValue1":1
 * }
 *
 * On extract:
 * targetObject.value1 and newValue is created (renamed), since it does not exist before.
 * The value1 is not known in the extracting context.
 */
public class MappingProcessor
{
    /**
     * The maximum JSON key length.
     */
    public static final int MAX_JSON_KEY_LEN = 256;

    public static final String EXCEPTION_MSG_ROOT_MAPPING_IN_COMBINATION_WITH_OTHER = "Target root mapping in combination with other mapping is not permitted.";
    public static final String EXCEPTION_MSG_MAPPING_NULL_NOR_EMPTY = "Mapping must not be neither null nor empty!";

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
        ensureValidParameter(sourceDocument, mappings);

        documentIndexer.wrap(targetDocument);
        final MsgPackTree targetTree = documentIndexer.index();

        documentExtractor.wrap(targetTree, sourceDocument);
        final MsgPackTree mergedDocumentTree = documentExtractor.extract(mappings);

        return writeResult(mergedDocumentTree);
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

        return writeResult(extractedDocumentTree);
    }

    /**
     * Writes the document, which corresponds to the resulting message pack tree, into the result buffer.
     *
     * @param resultingTree the resulting message pack tree
     * @return the length of the written message pack document
     */
    private int writeResult(MsgPackTree resultingTree)
    {
        treeWriter.wrap(resultingTree);
        final int resultLen = treeWriter.write();

        clear();
        return resultLen;
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
            throw new MappingException(EXCEPTION_MSG_MAPPING_NULL_NOR_EMPTY);
        }

        final boolean hasRootMapping = Arrays.stream(mappings)
                                             .anyMatch(mapping -> mapping.isRootTargetMapping());
        if (hasRootMapping && mappings.length > 1)
        {
            throw new MappingException(EXCEPTION_MSG_ROOT_MAPPING_IN_COMBINATION_WITH_OTHER);
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
