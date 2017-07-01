package io.zeebe.msgpack.mapping;

import static io.zeebe.msgpack.mapping.MsgPackTreeNodeIdConstructor.construct;

import org.agrona.DirectBuffer;
import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.query.MsgPackQueryExecutor;
import io.zeebe.msgpack.query.MsgPackTraverser;

/**
 * <p>
 * Represents an message pack document extractor.
 * </p>
 *
 * <p>
 * The extractor can wrap a message pack document, which is stored in a {@link DirectBuffer} and
 * extract parts of this wrapped document with help of the given {@link Mapping} objects.
 * The extracted parts are stored in a {@link MsgPackTree} object,
 * which is returned after calling {@link #extract(Mapping...)}.
 * </p>
 *
 * <p>
 * It is also possible that the extractor wraps an already existing {@link MsgPackTree} object and a
 * message pack document on which the extracting should be done. The extracted parts are stored in the wrapped
 * tree. This means nodes can be added or replaced in the wrapped message pack tree.
 * </p>
 *
 * <p>
 * A {@link Mapping} consist of a source json query and a target json path mapping. The source json query must match
 * on the wrapped message pack document. The matching value will be stored in the resulting {@link MsgPackTree}
 * object, the tree is either the wrapped tree or a tree which only consists of the extracted values.
 * </p>
 *
 * <p>
 * The resulting {@link MsgPackTree} consist of nodes for each part in the target json path mapping
 * and leafs, which corresponds to the leaf in the target json path mapping.
 * These leafs contains the values of the matched source json query, form the wrapped message pack document.
 * </p>
 *
 * <p>
 * Example:
 *
 * <pre>
 *{@code Wrapped document:
 *  {
 *     "sourceObject":{
 *         "foo":"bar"
 *     },
 *     "value1":1
 *  }
 *}
 * </pre>
 *
 * <pre>
 *{@code Mappings:
 *  $.sourceObject -> $.targetObject.value1
 *  $.value1 -> $.newValue1
 *}
 * </pre>
 *
 * <pre>
 *{@code
 * Then the resulting tree will look like the following:
 *
 *           $
 *        /     \
 *  newValue1   targetObject
 *    |           \
 *    1           value1
 *                 \
 *                 {"foo":"bar"}
 *}
 * </pre>
 *</p>
 */
public final class MsgPackDocumentExtractor
{
    public static final String EXCEPTION_MSG_MAPPING_DOES_NOT_MATCH = "No data found for query %s.";
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
                                               .split(MsgPackTreeNodeIdConstructor.JSON_PATH_SEPARATOR_REGEX);
            String nodeId, parentId = "";
            int index = 0;
            for (String nodeName : targetPath)
            {
                nodeId = createParentRelation(parentId, nodeName);

                final boolean isLeaf = index + 1 >= targetPath.length;
                if (isLeaf)
                {
                    executeLeafMapping(mapping.getSource());
                    documentTreeReference.addLeafNode(nodeId,
                                                      (long) queryExecutor.currentResultPosition(),
                                                      queryExecutor.currentResultLength());
                }
                parentId = nodeId;
                index++;
            }
        }
        return documentTreeReference;
    }

    /**
     * Creates the parent relation for the given node.
     * <p>
     * If the nodeName is an integer, this indicates that the parent node is an array, the nodeName
     * is in this case the index in the array. For that the a array parent node will be created and the
     * node will be added as child.
     * <p>
     * Is the nodeName not a integer this means the parent is a map
     * (or if the parent is empty the current node is root which has no parent). A map parent node is
     * added and the current node will added to the map node.
     *
     * Returns the constructed new node id for the current node.
     *
     * @param parentId the id of the parent
     * @param nodeName the name of the current node
     * @return the new node id consist of the parent id and the node name
     */
    private String createParentRelation(String parentId, String nodeName)
    {
        final String nodeId;

        if (parentId.isEmpty())
        {
            nodeId = nodeName;
        }
        else
        {
            final boolean isArrayNode = isIndex(nodeName);
            if (isArrayNode)
            {
                documentTreeReference.addArrayNode(parentId);
            }
            else
            {
                documentTreeReference.addMapNode(parentId);
            }
            nodeId = construct(parentId, nodeName);
            documentTreeReference.addChildToNode(nodeName, parentId);
        }
        return nodeId;
    }

    private boolean isIndex(String nodeName)
    {
        final int len = nodeName.length();
        for (int i = 0; i < len; i++)
        {
            final char currentChar = nodeName.charAt(0);
            if (currentChar < '0' || currentChar > '9')
            {
                return false;
            }
        }
        return true;
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
            final DirectBuffer expression = jsonPathQuery.getExpression();
            throw new MappingException(String.format(EXCEPTION_MSG_MAPPING_DOES_NOT_MATCH,
                                                     expression.getStringWithoutLengthUtf8(0, expression.capacity())));
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
