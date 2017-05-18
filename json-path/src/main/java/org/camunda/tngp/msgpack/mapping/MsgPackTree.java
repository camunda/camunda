package org.camunda.tngp.msgpack.mapping;

import static org.camunda.tngp.msgpack.mapping.MsgPackNodeType.EXISTING_LEAF_NODE;
import static org.camunda.tngp.msgpack.mapping.MsgPackNodeType.EXTRACTED_LEAF_NODE;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;

/**
 * Represents a tree data structure, for a msg pack document.
 *
 * The nodes of the tree can be either a real node, which has child's, or a leaf, which has a mapping
 * in the corresponding msg pack document to his value.
 *
 * The message pack document tree can be created from scratch from a underlying document.
 * This can be done with the {@link MsgPackDocumentIndexer}. It can also be constructed from only a port
 * of a message pack document. This can be done with the {@link MsgPackDocumentExtractor}.
 *
 * The message pack tree can consist from two different message pack documents.
 * The underlying document, from which the tree is completely build and the extract document,
 * which can be a part of another message pack document. The tree representation of the extract document
 * will be as well added to the current message pack tree object.
 *
 * Since the leafs contains a mapping, which consist of position and length, it is necessary that
 * both documents are available for the message pack tree, so the leaf value can be resolved later.
 * The leafs have to be distinguished, is it a leaf from the underlying document or is it
 * from the extract document. For this distinction the {@link MsgPackNodeType#EXISTING_LEAF_NODE} and
 * {@link MsgPackNodeType#EXTRACTED_LEAF_NODE} are used.
 *
 */
public class MsgPackTree
{
    protected final Map<String, MsgPackNodeType> nodeTypeMap; //Bytes2LongHashIndex nodeTypeMap;
    protected final Map<String, Set<String>> nodeChildsMap;
    protected final Map<String, Long>  leafMap; //Bytes2LongHashIndex leafMap;

    protected DirectBuffer underlyingDocument = new UnsafeBuffer(0, 0);
    protected DirectBuffer extractDocument;

    public MsgPackTree()
    {
        nodeTypeMap = new HashMap<>();
        nodeChildsMap = new HashMap<>();
        leafMap = new HashMap<>();
    }

    public void wrap(DirectBuffer underlyingDocument)
    {
        clear();
        this.underlyingDocument.wrap(underlyingDocument);
    }

    public void clear()
    {
        extractDocument = null;
        nodeChildsMap.clear();
        nodeTypeMap.clear();
        leafMap.clear();
    }

    public Set<String> getChilds(String nodeId)
    {
        return nodeChildsMap.get(nodeId);
    }

    public void addLeafNode(String nodeId, long position, int length)
    {
        leafMap.put(nodeId, (position << 32) | length);
        nodeTypeMap.put(nodeId, extractDocument == null ? EXISTING_LEAF_NODE : EXTRACTED_LEAF_NODE);
    }

    private void addParentNode(String nodeId, MsgPackNodeType nodeType)
    {
        nodeTypeMap.put(nodeId, nodeType);
        if (!nodeChildsMap.containsKey(nodeId))
        {
            nodeChildsMap.put(nodeId, new LinkedHashSet<>());
        }
    }

    public void addMapNode(String nodeId)
    {
        addParentNode(nodeId, MsgPackNodeType.MAP_NODE);
    }

    public void addArrayNode(String nodeId)
    {
        addParentNode(nodeId, MsgPackNodeType.ARRAY_NODE);
    }

    public void addChildToNode(String childName, String parentId)
    {
        nodeChildsMap.get(parentId).add(childName);
    }


    public boolean isLeaf(String nodeId)
    {
        return leafMap.containsKey(nodeId);
    }

    public boolean isArrayNode(String nodeId)
    {
        final MsgPackNodeType msgPackNodeType = nodeTypeMap.get(nodeId);
        return msgPackNodeType != null && msgPackNodeType == MsgPackNodeType.ARRAY_NODE;
    }

    public boolean isMapNode(String nodeId)
    {
        final MsgPackNodeType msgPackNodeType = nodeTypeMap.get(nodeId);
        return msgPackNodeType != null && msgPackNodeType == MsgPackNodeType.MAP_NODE;
    }

    public void setExtractDocument(DirectBuffer documentBuffer)
    {
        this.extractDocument = documentBuffer;
    }

    public void writeLeafMapping(MsgPackWriter writer, String leafId)
    {
        final long mapping = leafMap.get(leafId);
        final MsgPackNodeType nodeType = nodeTypeMap.get(leafId);
        final int position = (int) (mapping >> 32);
        final int length = (int) mapping;
        DirectBuffer relatedBuffer = underlyingDocument;
        if (nodeType == EXTRACTED_LEAF_NODE)
        {
            relatedBuffer = extractDocument;
        }
        writer.writeRaw(relatedBuffer, position, length);
    }
}
