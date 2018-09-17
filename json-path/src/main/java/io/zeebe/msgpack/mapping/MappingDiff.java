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

import static io.zeebe.msgpack.mapping.MsgPackTreeNodeIdConstructor.construct;

import io.zeebe.msgpack.jsonpath.JsonPathToken;
import io.zeebe.msgpack.jsonpath.JsonPathTokenVisitor;
import io.zeebe.msgpack.jsonpath.JsonPathTokenizer;
import java.util.Set;
import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

public class MappingDiff implements MsgPackDiff, JsonPathTokenVisitor {

  private static final int RESULT_ENTRY_LENGTH = 2 * BitUtil.SIZE_OF_INT;

  private Mapping[] mappings;
  private DirectBuffer document;
  private final ExpandableArrayBuffer mappingResults = new ExpandableArrayBuffer();

  private final JsonPathTokenizer tokenizer = new JsonPathTokenizer();

  public void init(Mapping[] mappings, DirectBuffer document) {
    this.mappings = mappings;
    this.document = document;
    this.parentId = "";
    this.nodeId = "";
  }

  public int getResultOffset(int mappingIndex) {
    return mappingResults.getInt(mapToResultIndex(mappingIndex));
  }

  public int getResultLength(int mappingIndex) {
    return mappingResults.getInt(mapToResultIndex(mappingIndex) + BitUtil.SIZE_OF_INT);
  }

  private static int mapToResultIndex(int mappingIndex) {
    return mappingIndex * RESULT_ENTRY_LENGTH;
  }

  public void setResultOffset(int mappingIndex, int offset) {
    mappingResults.putInt(mapToResultIndex(mappingIndex), offset);
  }

  public void setResultLength(int mappingIndex, int length) {
    mappingResults.putInt(mapToResultIndex(mappingIndex) + BitUtil.SIZE_OF_INT, length);
  }

  private String nodeId;
  private String parentId;
  private int ourDocumentId;
  private MsgPackTree treeToMergeInto;

  @Override
  public void visit(
      JsonPathToken type, DirectBuffer valueBuffer, int valueOffset, int valueLength) {

    if (type == JsonPathToken.LITERAL || type == JsonPathToken.ROOT_OBJECT) {
      final String nodeName = valueBuffer.getStringWithoutLengthUtf8(valueOffset, valueLength);
      nodeId = createParentRelation(treeToMergeInto, parentId, nodeName);
      parentId = nodeId;
    }
  }

  /**
   * Creates the parent relation for the given node.
   *
   * <p>If the nodeName is an integer, this indicates that the parent node is an array, the nodeName
   * is in this case the index in the array. For that the a array parent node will be created and
   * the node will be added as child.
   *
   * <p>Is the nodeName not a integer this means the parent is a map (or if the parent is empty the
   * current node is root which has no parent). A map parent node is added and the current node will
   * added to the map node.
   *
   * <p>Returns the constructed new node id for the current node.
   *
   * @param parentId the id of the parent
   * @param nodeName the name of the current node
   * @return the new node id consist of the parent id and the node name
   */
  private String createParentRelation(MsgPackTree tree, String parentId, String nodeName) {
    final String nodeId;

    if (parentId.isEmpty()) {
      nodeId = nodeName;
    } else {
      final boolean isIndex = isIndex(nodeName);

      if (isIndex) {
        if (!tree.isMapNode(parentId)) {
          tree.addArrayNode(parentId);
        }
      } else {
        tree.addMapNode(parentId);
      }
      nodeId = construct(parentId, nodeName);
      tree.addChildToNode(nodeName, parentId);
    }
    return nodeId;
  }

  private boolean isIndex(String nodeName) {
    final int len = nodeName.length();
    for (int i = 0; i < len; i++) {
      final char currentChar = nodeName.charAt(i);
      if (currentChar < '0' || currentChar > '9') {
        return false;
      }
    }
    return true;
  }

  @Override
  public void mergeInto(MsgPackTree document) {
    ourDocumentId = document.addDocument(this.document);
    this.treeToMergeInto = document;

    for (int i = 0; i < mappings.length; i++) {
      final Mapping mapping = mappings[i];
      final DirectBuffer targetQueryString = mapping.getTargetQueryBuffer();

      // TODO: not great
      parentId = "";
      nodeId = "";

      /*
       * Optimization idea: Tokenization of the target query should be done only
       *   once when the mapping is created
       */

      // creates the structure for intermediary nodes
      tokenizer.tokenize(targetQueryString, 0, targetQueryString.capacity(), this);

      switch (mapping.getType()) {
        case COLLECT:
          // TODO: ugly, need more high-level methods on MsgPackTree
          parentId = nodeId;

          if (document.isMapNode(parentId)) {
            document.removeContainerNode(parentId);
          }
          document.addArrayNode(parentId);

          final Set<String> numChildren = document.getChilds(nodeId);
          final String nodeName = Integer.toString(numChildren.size());
          nodeId = construct(parentId, nodeName);
          document.addChildToNode(nodeName, parentId);
          document.addLeafNode(nodeId, ourDocumentId, getResultOffset(i), getResultLength(i));
          break;
        case PUT:
        default:
          document.addLeafNode(nodeId, ourDocumentId, getResultOffset(i), getResultLength(i));
          break;
      }
    }
  }
}
