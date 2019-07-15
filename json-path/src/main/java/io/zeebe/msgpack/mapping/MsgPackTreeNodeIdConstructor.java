/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.mapping;

/** Represents a static constructor to constructing the node ids for the {@link MsgPackTree}. */
public class MsgPackTreeNodeIdConstructor {
  public static final String JSON_PATH_SEPARATOR = "[";
  public static final String JSON_PATH_SEPARATOR_END = "]";

  public static String construct(String parentId, String nodeName) {
    return parentId.isEmpty()
        ? nodeName
        : parentId + JSON_PATH_SEPARATOR + nodeName + JSON_PATH_SEPARATOR_END;
  }

  public static String getLastParentId(String nodeId) {
    final int indexOfLastSeparator = nodeId.lastIndexOf(JSON_PATH_SEPARATOR);
    final String parentId = nodeId.substring(0, indexOfLastSeparator);
    return parentId;
  }
}
