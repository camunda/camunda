package org.camunda.tngp.msgpack.mapping;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;

import org.agrona.DirectBuffer;
import org.camunda.tngp.msgpack.query.MsgPackTokenVisitor;
import org.camunda.tngp.msgpack.query.MsgPackTraverser;
import org.camunda.tngp.msgpack.spec.MsgPackToken;
import org.camunda.tngp.msgpack.spec.MsgPackType;

/**
 * Represents an message pack document indexer. During the indexing of
 * an existing message pack document an {@link MsgPackTree} object will be constructed,
 * which corresponds to the structure of the message pack document.
 *
 * Example:
 *
 * Say we have the following json as message pack document:
 * {
 *     "object1":{
 *         "field1": true,
 *         "array":[ 1,2,3]
 *     }
 *     "field2" : "String"
 * }
 *
 * The {@link #index()} method will return an {@link MsgPackTree} object which has the following structure:
 *
 *          $
 *       /     \
 *   field2   object1
 *     |     /       \
 *  String  field1   array
 *           |      /  |  \
 *          true    1  2   3
 *
 * Then this correspond to the following message pack tree structure:
 *
 * <p>NodeTypes:</p>
 *
 * 1object1 : MAP_NODE
 * 2field1 : LEAF
 * 2array : ARRAY_NODE
 * 1field2: LEAF
 *
 * <p>NodeChildsMap:</p>
 *
 * 1object1: field1, array
 * 2array : 2array1, 2array2, 2array3,
 *
 * <p>LeafMap:</p>
 * 2field1: mapping
 * 2array1: mapping
 * 2array2: mapping
 * 2array3: mapping
 * 1field2: mapping
 *
 */
public final class MsgPackDocumentIndexer implements MsgPackTokenVisitor
{
    /**
     * The message pack tree which is constructed via the indexing of the message pack document.
     */
    protected MsgPackTree msgPackTree;

    /**
     * The last key for the node, since
     * the node is divided in separate MsgPackTokens.
     */
    protected final byte lastKey[] = new byte[MappingProcessor.MAX_JSON_KEY_LEN];

    /**
     * The length of the last key.
     */
    protected int lastKeyLen;

    /**
     *  Contains the current parents of the current node.
     *  A node become a parent if the node is of type MAP or ARRAY.
     *  This node will be added several times, corresponding to the size of the MsgPackToken#size of this node.
     */
    protected Deque<String> parentsStack = new ArrayDeque<>();

    /**
     * Indicates if the next MsgPackToken is a value.
     */
    protected boolean nextIsValue;

    /**
     * Indicates if the current value belongs to an array.
     */
    protected boolean isArrayValue = false;

    /**
     * The current index of the value, which belongs to an array.
     */
    protected final AtomicLong currentIndex = new AtomicLong(0);

    /**
     * The traverser which is used to index the message pack document.
     */
    protected final MsgPackTraverser traverser = new MsgPackTraverser();

    public MsgPackDocumentIndexer()
    {
        msgPackTree = new MsgPackTree();

        lastKey[0] = '$';
        lastKeyLen = 1;
        nextIsValue = true;
    }

    public void wrap(DirectBuffer msgPackDocument)
    {
        msgPackTree.wrap(msgPackDocument);
        traverser.wrap(msgPackDocument, 0, msgPackDocument.capacity());
    }

    public MsgPackTree index()
    {
        traverser.traverse(this);
        return msgPackTree;
    }

    @Override
    public void visitElement(int position, MsgPackToken currentValue)
    {
        final MsgPackType currentValueType = currentValue.getType();
        if (nextIsValue)
        {
            if (currentValueType == MsgPackType.MAP)
            {
                processObjectNode(currentValue);
            }
            else if (currentValueType == MsgPackType.ARRAY)
            {
                processArrayNode(currentValue);
            }
            else
            {
                processValueNode(position, currentValue);
            }
        }
        else if (currentValueType == MsgPackType.STRING)
        {
            final DirectBuffer valueBuffer = currentValue.getValueBuffer();
            lastKeyLen = valueBuffer.capacity();
            valueBuffer.getBytes(0, lastKey, 0, lastKeyLen);
            nextIsValue = true;
        }
    }

    /**
     * The current node is a array and the currentValue references the ARRAY token.
     * The string value, which identifies the node, was read before.
     *
     * This method process the array node and inserts the node properties
     * into the tree. It will add the nodeType (type = array), create
     * a new entry in the node childs map with a empty list, since this node represents a new parent node.
     *
     * If necessary it will add this node to his parent child list and pops a parent from the stack.
     * It will also push the current node identifier several times to the parent stack.
     * The count is related to the child count which is equal to the MsgPackToken#size if the current token is of
     * type ARRAY.
     *
     * An flag will be toggled, which indicates that the next elements are belong to an array.
     * This is necessary since arrays contains no names for there values.
     *
     * @param currentValue the message pack token which represents an array
     */
    private void processArrayNode(MsgPackToken currentValue)
    {
        final int childSize = currentValue.getSize();
        addNewParent(childSize, true);

        currentIndex.set(0);
        // no keys in arrays
        nextIsValue = true;
        isArrayValue = true;
    }

    /**
     * The current node is a map and the currentValue references the MAP token.
     * The string value, which identifies the node, was read before.
     *
     * This method process the object node and inserts the node properties
     * into the tree data structure. It will add the nodeType (type = map), create
     * a new entry in the node childs map with a empty list, since this node represents a new parent node.
     *
     * If necessary it will add this node to his parent child list and pops a parent from the stack.
     * It will also push the current node identifier several times to the parent stack.
     * The count is related to the child count which is equal to the MsgPackToken#size if the current token is of
     * type MAP.
     *
     * @param currentValue the message pack token which represents a map
     */
    private void processObjectNode(MsgPackToken currentValue)
    {
        final int childSize = currentValue.getSize();
        addNewParent(childSize, false);
        nextIsValue = false;
    }

    /**
     * Creates and returns the nodeId, which consist of the parentId and the node name.
     *
     * @param nodeName the name of the current node
     * @return the id of the current node
     */
    private String createNodeId(String nodeName)
    {
        if (!parentsStack.isEmpty())
        {
            final String parentId = parentsStack.peek();
            return parentId + nodeName;
        }
        return nodeName;
    }

    /**
     * Adds a new parent of the given type with the given child size to the internal data structure.
     *
     * @param childCount the count of child's
     * @param isArray indicates if the current parent node represents an array or not
     */
    private void addNewParent(int childCount, boolean isArray)
    {
        final String nodeName = isArrayValue
                        ? "" + currentIndex.getAndIncrement()
                        : getNodeName(lastKey, lastKeyLen);
        final String nodeId = createNodeId(nodeName);

        if (isArray)
        {
            msgPackTree.addArrayNode(nodeId);
        }
        else
        {
            msgPackTree.addMapNode(nodeId);
        }

        if (!parentsStack.isEmpty())
        {
            final String parentId = parentsStack.pop();
            msgPackTree.addChildToNode(nodeName, parentId);
        }

        for (int i = 0; i < childCount; i++)
        {
            parentsStack.push(nodeId);
        }
    }

    /**
     * The current node is a simple value and the currentValue reference to a token
     * of another type except MAP and ARRAY.
     *
     * This method process the value node and inserts the node properties into the
     * tree data structure. It will add the nodeType ( type = leaf), pops a
     * parent of the stack and add this node to his child list. Also the leaf mapping will be
     * created and inserted.
     *
     *
     * @param position the position of the token in the payload
     * @param currentValue the message pack token which represents a simple value
     */
    private void processValueNode(long position, MsgPackToken currentValue)
    {
        final String parentId = parentsStack.pop();
        String nodeName;
        if (isArrayValue)
        {
            final long index = currentIndex.getAndIncrement();
            nodeName = getNodeName(lastKey, lastKeyLen);
            if (parentId.contains(nodeName))
            {
                nodeName = "" + index;
            }
            if (!(parentId).equals(parentsStack.peek()))
            {
                isArrayValue = false;
                nextIsValue = false;
            }
        }
        else
        {
            nodeName = getNodeName(lastKey, lastKeyLen);
            nextIsValue = false;
        }

        final String nodeId = parentId + nodeName;
        msgPackTree.addChildToNode(nodeName, parentId);
        msgPackTree.addLeafNode(nodeId, position, currentValue.getTotalLength());
    }

    /**
     * Clears the preprocessor and resets to the initial state.
     */
    public void clear()
    {
        lastKey[0] = '$';
        lastKeyLen = 1;
        nextIsValue = true;
        currentIndex.set(0);
        parentsStack.clear();
    }

    /**
     * Returns the node name.
     * @param bytes the bytes which contains the name
     * @param length the length of the name
     * @return the name as string
     */
    private String getNodeName(byte[] bytes, int length)
    {
        final byte nameBytes[] = Arrays.copyOf(bytes, length);
        return new String(nameBytes);
    }
}
