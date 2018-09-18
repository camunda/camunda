/*
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
