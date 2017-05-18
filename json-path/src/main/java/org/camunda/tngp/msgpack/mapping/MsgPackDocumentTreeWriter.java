package org.camunda.tngp.msgpack.mapping;

import java.util.Set;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;

/**
 * Represents an message pack document tree writer.
 *
 * The writer can wrap an existing {@link MsgPackTree} object with the
 * {@link #wrap(MsgPackTree)} method.
 *
 * On calling the {@link #write()} method the writer will write the corresponding
 * message pack document into a result buffer and return the size of the written document.
 *
 * The result buffer is available via the {@link #getResult()} method.
 *
 */
public class MsgPackDocumentTreeWriter
{
    protected MsgPackTree documentTree;
    protected final MsgPackWriter msgPackWriter;
    protected final MutableDirectBuffer resultingBuffer;
    protected final DirectBuffer nodeName;

    public MsgPackDocumentTreeWriter(int initialDocumentSize)
    {
        this.msgPackWriter = new MsgPackWriter();
        this.resultingBuffer = new ExpandableArrayBuffer(initialDocumentSize);
        this.nodeName = new UnsafeBuffer(0, 0);
    }

    public void wrap(MsgPackTree documentTree)
    {
        this.documentTree = documentTree;
    }

    /**
     * Writes the wrapped message pack tree into the result buffer.
     * Returns the size of the written message pock document.
     * The result buffer is available via the {@link #getResult()} method.
     *
     * @return the size of the message pack document
     */
    public int write()
    {
        final String startNode = Mapping.JSON_ROOT_PATH;
        msgPackWriter.wrap(resultingBuffer, 0);

        writeNode("", startNode, false);
        return msgPackWriter.getOffset();
    }

    /**
     * Recursive method to write the message pack document tree into the result buffer.
     *
     * The writing will start with parentId "" and "$" as nodeName, which represents the root.
     * The parentId and the nodeName is equal to the node identifier.
     *
     * With help of the tree it can be determined if the current node
     * is of type MAP, ARRAY or LEAF. If the node is of type MAP or ARRAY the map or array header will be writen
     * with the size of existing child's. After that the child's are recursively written.
     * <p>
     * If the node is of type LEAF the leaf value is written to the result buffer.
     *
     * @param parentId the id of the parent node
     * @param nodeName the name of the current node
     * @param isArray  indicates if the current node belongs to an array
     */
    private void writeNode(String parentId, String nodeName, boolean isArray)
    {
        if (!parentId.isEmpty() && !isArray)
        {
            this.nodeName.wrap(nodeName.getBytes());
            msgPackWriter.writeString(this.nodeName);
        }

        final String nodeId = parentId + nodeName;
        if (documentTree.isLeaf(nodeId))
        {
            documentTree.writeLeafMapping(msgPackWriter, nodeId);
        }
        else
        {
            final boolean isArrayNode = documentTree.isArrayNode(nodeId);
            final Set<String> childs = documentTree.getChilds(nodeId);
            if (isArrayNode)
            {
                msgPackWriter.writeArrayHeader(childs.size());
            }
            else
            {
                msgPackWriter.writeMapHeader(childs.size());
            }

            for (String child : childs)
            {
                writeNode(nodeId, child, isArrayNode);
            }
        }
    }

    public MutableDirectBuffer getResult()
    {
        return resultingBuffer;
    }
}
