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

import io.zeebe.msgpack.spec.MsgPackWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.collections.Object2IntHashMap;

/**
 * Represents a tree data structure, for a msg pack document.
 *
 * <p>The nodes of the tree can be either a real node, which has child's, or a leaf, which has a
 * mapping in the corresponding msg pack document to his value.
 *
 * <p>The message pack document tree can be created from scratch from a underlying document. This
 * can be done with the {@link MsgPackDocumentIndexer}. It can also be constructed from only a port
 * of a message pack document. This can be done with the {@link MsgPackDocumentExtractor}.
 *
 * <p>The message pack tree can consist from two different message pack documents. The underlying
 * document, from which the tree is completely build and the extract document, which can be a part
 * of another message pack document. The tree representation of the extract document will be as well
 * added to the current message pack tree object.
 *
 * <p>Since the leafs contains a mapping, which consist of position and length, it is necessary that
 * both documents are available for the message pack tree, so the leaf value can be resolved later.
 * The leafs have to be distinguished, is it a leaf from the underlying document or is it from the
 * extract document. For this distinction the {@link MsgPackNodeType#EXISTING_LEAF_NODE} and {@link
 * MsgPackNodeType#EXTRACTED_LEAF_NODE} are used.
 */
public class MsgPackTree implements MsgPackDiff {
  protected final Map<String, MsgPackNodeType> nodeTypeMap; // Bytes2LongHashIndex nodeTypeMap;
  protected final Map<String, Set<String>> nodeChildsMap;
  protected final Map<String, Long> leafMap; // Bytes2LongHashIndex leafMap;
  protected final Object2IntHashMap<String> leafDocumentSources;

  private DirectBuffer[] documents = new DirectBuffer[0];

  public MsgPackTree() {
    nodeTypeMap = new HashMap<>();
    nodeChildsMap = new HashMap<>();
    leafMap = new HashMap<>();
    leafDocumentSources = new Object2IntHashMap<>(-1);
  }

  public int size() {
    return nodeTypeMap.size();
  }

  public void clear() {
    nodeChildsMap.clear();
    nodeTypeMap.clear();
    leafMap.clear();
    leafDocumentSources.clear();
    documents = new DirectBuffer[0];
  }

  /** @return the names (not IDs) of the child nodes */
  public Set<String> getChildren(String nodeId) {
    return nodeChildsMap.get(nodeId);
  }

  public int addDocument(DirectBuffer document) {
    documents = Arrays.copyOf(documents, documents.length + 1);
    documents[documents.length - 1] = document;
    return documents.length - 1;
  }

  private void addContainerNode(String nodeId, MsgPackNodeType nodeType) {
    nodeTypeMap.put(nodeId, nodeType);
    nodeChildsMap.put(nodeId, new LinkedHashSet<>());
  }

  private void addChildToNode(String parentId, String childName) {
    if (!parentId.isEmpty()) {
      nodeChildsMap.get(parentId).add(childName);
    }
  }

  public boolean isValueNode(String nodeId) {
    return nodeTypeMap.get(nodeId) == MsgPackNodeType.VALUE;
  }

  public boolean isArrayNode(String nodeId) {
    return nodeTypeMap.get(nodeId) == MsgPackNodeType.ARRAY;
  }

  public boolean isMapNode(String nodeId) {
    return nodeTypeMap.get(nodeId) == MsgPackNodeType.MAP;
  }

  public void writeValueNode(MsgPackWriter writer, String nodeId) {
    final long mapping = leafMap.get(nodeId);
    final int position = (int) (mapping >> 32);
    final int length = (int) mapping;

    final int documentId = leafDocumentSources.getValue(nodeId);
    final DirectBuffer sourceDocument = documents[documentId];

    writer.writeRaw(sourceDocument, position, length);
  }

  /** Always replaces containers (object/array), unless it is the root object */
  @Override
  public void mergeInto(MsgPackTree other) {
    /*
     * This method is critical for the performance of document merging
     * and extraction, so optimizations should be made here.
     */

    final int newDocumentOffset =
        other.documents.length; // => so we can map other document ids to this document id

    for (DirectBuffer ourDocument : documents) {
      other.addDocument(ourDocument);
    }

    for (Map.Entry<String, MsgPackNodeType> leafMapEntry : nodeTypeMap.entrySet()) {
      final String key = leafMapEntry.getKey();
      final MsgPackNodeType nodeType = leafMapEntry.getValue();

      // hack: do not convert maps in the current tree to arrays
      // use case: map keys that are digits
      if (!(other.nodeTypeMap.get(key) == MsgPackNodeType.MAP
          && nodeType == MsgPackNodeType.ARRAY)) {
        other.nodeTypeMap.put(key, nodeType);
      }

      other.leafMap.remove(
          key); // => remove everything that was a leaf previously => is going to be restored by
      // putting all leafs from the other map

      final int otherDocumentSource = leafDocumentSources.getValue(key);
      if (otherDocumentSource >= 0) {
        other.leafDocumentSources.put(key, otherDocumentSource + newDocumentOffset);
      }
    }
    other.leafMap.putAll(leafMap);

    for (Map.Entry<String, Set<String>> nodeChildsEntry : nodeChildsMap.entrySet()) {
      final String key = nodeChildsEntry.getKey();

      // if we change the following condition to if (nodeChildsMap.containsKey(key))
      // we get a deep merge
      if (key.equals(Mapping.JSON_ROOT_PATH)) {
        other
            .nodeChildsMap
            .computeIfAbsent(key, (k) -> new LinkedHashSet<>())
            .addAll(nodeChildsEntry.getValue());
      } else {
        other.nodeChildsMap.put(key, nodeChildsEntry.getValue());
      }
    }
  }

  /** Keeps any children, e.g. when converting MAP to ARRAY */
  public void convertToArrayNode(String nodeId) {
    concertToContainer(nodeId, MsgPackNodeType.ARRAY);
  }

  /** Keeps any children, e.g. when converting ARRAY to MAP */
  public void convertToMapNode(String nodeId) {
    concertToContainer(nodeId, MsgPackNodeType.MAP);
  }

  private void concertToContainer(String nodeId, MsgPackNodeType containerType) {
    final MsgPackNodeType priorType = nodeTypeMap.get(nodeId);

    if (priorType == MsgPackNodeType.VALUE) {
      leafMap.remove(nodeId);
      nodeChildsMap.put(nodeId, new LinkedHashSet<>());
    }

    nodeTypeMap.put(nodeId, containerType);
  }

  /**
   * Creates or converts the addressed node to an array and appends the value as a new element.
   * Replaces a previously existing non-array node completely.
   */
  public String appendToArray(
      String parentId, String arrayNodeName, int documentId, int elementOffset, int elementLength) {

    final String arrayNodeId = construct(parentId, arrayNodeName);

    if (hasNode(arrayNodeId)) {
      if (!isArrayNode(arrayNodeId)) {
        clearChildren(arrayNodeId);
        convertToArrayNode(arrayNodeId);
      }
    } else {
      addArrayNode(parentId, arrayNodeName);
    }

    final int currentArrayElements = nodeChildsMap.get(arrayNodeId).size();
    final String nodeName = Integer.toString(currentArrayElements);

    return addValueNode(arrayNodeId, nodeName, documentId, elementOffset, elementLength);
  }

  public String addArrayNode(String parentId, String arrayNodeName) {
    final String nodeId = construct(parentId, arrayNodeName);

    addContainerNode(nodeId, MsgPackNodeType.ARRAY);
    addChildToNode(parentId, arrayNodeName);

    return nodeId;
  }

  public String addValueNode(
      String parentId, String nodeName, int documentId, int valueOffset, int valueLength) {
    final String nodeId = construct(parentId, nodeName);

    leafMap.put(nodeId, ((long) valueOffset << 32) | valueLength);
    leafDocumentSources.put(nodeId, documentId);
    nodeTypeMap.put(nodeId, MsgPackNodeType.VALUE);

    addChildToNode(parentId, nodeName);

    return nodeId;
  }

  public String addMapNode(String parentId, String nodeName) {
    final String nodeId = construct(parentId, nodeName);

    addContainerNode(nodeId, MsgPackNodeType.MAP);
    addChildToNode(parentId, nodeName);

    return nodeId;
  }

  private boolean isContainerNode(String nodeId) {
    final MsgPackNodeType nodeType = nodeTypeMap.get(nodeId);
    return nodeType == MsgPackNodeType.ARRAY || nodeType == MsgPackNodeType.MAP;
  }

  public boolean hasNode(String id) {
    return nodeTypeMap.containsKey(id);
  }

  public void clearChildren(String nodeId) {
    if (isContainerNode(nodeId)) {
      nodeChildsMap.get(nodeId).clear();
    }
  }
}
