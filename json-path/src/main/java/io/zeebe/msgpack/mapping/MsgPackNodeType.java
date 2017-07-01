package io.zeebe.msgpack.mapping;

/**
 * Represents the existing types for an message pack node.
 * Is used in combination with the {@link MsgPackTree}.
 */
public enum MsgPackNodeType
{
    MAP_NODE, ARRAY_NODE,

    /**
     * Nodes of type {@link #EXISTING_LEAF_NODE} represents leafs,
     * which are already exist in the message pack document.
     * These node types are used in indexing of a message pack document,
     * they have to be merged with new documents.
     */
    EXISTING_LEAF_NODE,

    /**
     * Nodes of type {@link #EXTRACTED_LEAF_NODE} represents leafs
     * which are extracted from a message pack document.
     * These leaf node is created in the new document.
     */
    EXTRACTED_LEAF_NODE
}
