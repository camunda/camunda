package io.zeebe.msgpack.mapping;

/**
 * Represents a static constructor to constructing the
 * node ids for the {@link MsgPackTree}.
 */
public class MsgPackTreeNodeIdConstructor
{
    public static final String JSON_PATH_SEPARATOR_REGEX = "[\\.\\[\\]]+";
    public static final String JSON_PATH_SEPARATOR = ".";

    public static String construct(String parentId, String nodeName)
    {
        return parentId + "." + nodeName;
    }
}
