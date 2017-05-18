package org.camunda.tngp.msgpack.mapping;

import org.agrona.DirectBuffer;
import org.camunda.tngp.msgpack.jsonpath.JsonPathQuery;
import org.camunda.tngp.msgpack.query.MsgPackQueryExecutor;
import org.camunda.tngp.msgpack.query.MsgPackTraverser;

/**
 * Represents an message pack document extractor.
 *
 * The extractor can wrap a message pack document, which is stored in a {@link DirectBuffer} and
 * extract parts of this wrapped document with help of the given {@link Mapping} objects.
 * The extracted parts are stored in a {@link MsgPackTree} object,
 * which is returned after calling {@link #extract(Mapping...)}.
 *
 * It is also possible that the extractor wraps an already existing {@link MsgPackTree} object and a
 * message pack document on which the extracting should be done. The extracted parts are stored in the wrapped
 * tree. This means nodes can be added or replaced in the wrapped message pack tree.
 *
 * A {@link Mapping} consist of a source json query and a target json path mapping. The source json query must match
 * on the wrapped message pack document. The matching value will be stored in the resulting {@link MsgPackTree}
 * object, the tree is either the wrapped tree or a tree which only consists of the extracted values.
 *
 * The resulting {@link MsgPackTree} consist of nodes for each part in the target json path mapping
 * and leafs, which corresponds to the leaf in the target json path mapping.
 * These leafs contains the values of the matched source json query, form the wrapped message pack document.
 *
 * Example:
 *
 * Wrapped document:
 * {
 *     "sourceObject":{
 *         "foo":"bar"
 *     },
 *     "value1":1
 * }
 * Mappings:
 *  $.sourceObject -> $.targetObject.value1
 *  $.value1 -> $.newValue1
 *
 * Then the resulting tree will look like the following:
 *
 *           $
 *        /     \
 *  newValue1   targetObject
 *    |           \
 *    1           value1
 *                 \
 *                 {"foo":"bar"}
 */
public final class MsgPackDocumentExtractor
{
    public static final String JSON_PATH_SEPARATOR = "\\.";
    public static final String EXCEPTION_MSG_MAPPING_DOES_NOT_MATCH = "No data found for query.";
    public static final String EXCEPTION_MSG_MAPPING_HAS_MORE_THAN_ONE_MATCHING_SOURCE = "JSON path mapping has more than one matching source.";

    /**
     * Holds the reference of the msg pack document tree, on which the extracted
     * values are stored. Could either hold a reference of a wrapped message pack document tree
     * or of the {@link #extractDocumentTree}, which is used if a new document tree should be
     * created from an extraction of an document.
     */
    private MsgPackTree documentTreeReference;

    /**
     * This message pack document tree will be used if only a messag pack document is wrapped
     * and parts of the document should be extracted.
     */
    private final MsgPackTree extractDocumentTree = new MsgPackTree();

    private final MsgPackTraverser traverser = new MsgPackTraverser();
    private final MsgPackQueryExecutor queryExecutor = new MsgPackQueryExecutor();

    /**
     * Wraps a existing message pack document tree and a message pack document, on which the extracting
     * should be executed.
     *
     * @param existingDocumentTree the tree on which the extracted parts are stored
     * @param extractDocument the document on which parts should be extracted
     */
    public void wrap(MsgPackTree existingDocumentTree, DirectBuffer extractDocument)
    {
        documentTreeReference = existingDocumentTree;
        documentTreeReference.setExtractDocument(extractDocument);
        traverser.wrap(extractDocument, 0, extractDocument.capacity());
    }

    public void wrap(DirectBuffer document)
    {
        documentTreeReference = extractDocumentTree;
        documentTreeReference.setExtractDocument(document);
        traverser.wrap(document, 0, document.capacity());
    }

    public MsgPackTree extract(Mapping ...mappings)
    {
        for (Mapping mapping : mappings)
        {
            final String targetPath[] = mapping.getTargetQueryString()
                                               .split(JSON_PATH_SEPARATOR);
            String parent, nodeId, parentId = "";
            int index = 0;
            for (String nodeName : targetPath)
            {
                final int indexOfOpeningBracket = nodeName.indexOf('[');
                final boolean isArrayNode = indexOfOpeningBracket != -1;
                final boolean isLeaf;

                if (isArrayNode)
                {
                    parent = nodeName.substring(0, indexOfOpeningBracket);
                    final String lastParent = parentId;
                    parentId = parentId + parent;
                    documentTreeReference.addChildToNode(parent, lastParent);
                    documentTreeReference.addArrayNode(parentId);

                    final int indexOfClosingBracket = nodeName.indexOf(']');
                    nodeName = nodeName.substring(indexOfOpeningBracket + 1, indexOfClosingBracket);
                    nodeId = parentId + nodeName;
                    documentTreeReference.addChildToNode(nodeName, parentId);
                    isLeaf = (++index >= targetPath.length);
                }
                else
                {
                    nodeId = parentId + nodeName;
                    if (index != 0)
                    {
                        documentTreeReference.addChildToNode(nodeName, parentId);
                    }
                    isLeaf = (index + 1 >= targetPath.length);
                }

                addNode(nodeId, isLeaf, mapping);
                parentId = nodeId;
                index++;
            }
        }
        return documentTreeReference;
    }

    /**
     * Adds the a node to the resulting tree.
     * The isLeaf flag indicates if the current node is a map or leaf. For leafs
     * the mapping will be executed and the corresponding leaf mapping will be added to the tree.
     *
     * @param nodeId  the id of the node
     * @param isLeaf  the flag which indicates whether the node is a leaf or not
     * @param mapping the mapping which is executed for a leaf node
     */
    private void addNode(String nodeId, boolean isLeaf, Mapping mapping)
    {
        if (isLeaf)
        {
            executeLeafMapping(mapping.getSource());
            documentTreeReference.addLeafNode(nodeId,
                                              (long) queryExecutor.currentResultPosition(),
                                              queryExecutor.currentResultLength());
        }
        else
        {
            documentTreeReference.addMapNode(nodeId);
        }
    }

    /**
     * Executes the given json path query.
     * The matching result is available in the query executor object.
     *
     * @param jsonPathQuery the query which should be executed
     */
    private void executeLeafMapping(JsonPathQuery jsonPathQuery)
    {
        queryExecutor.init(jsonPathQuery.getFilters(), jsonPathQuery.getFilterInstances());
        traverser.traverse(queryExecutor);

        if (queryExecutor.numResults() == 1)
        {
            queryExecutor.moveToResult(0);
        }
        else if (queryExecutor.numResults() == 0)
        {
            throw new MappingException(EXCEPTION_MSG_MAPPING_DOES_NOT_MATCH);
        }
        else
        {
            throw new IllegalStateException(EXCEPTION_MSG_MAPPING_HAS_MORE_THAN_ONE_MATCHING_SOURCE);
        }
        traverser.reset();
    }

    public void clear()
    {
        this.documentTreeReference.clear();
        this.traverser.reset();
    }
}
