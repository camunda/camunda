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

  public Set<String> getChilds(String nodeId) {
    return nodeChildsMap.get(nodeId);
  }

  public int addDocument(DirectBuffer document) {
    documents = Arrays.copyOf(documents, documents.length + 1);
    documents[documents.length - 1] = document;
    return documents.length - 1;
  }

  public void addLeafNode(String nodeId, int documentId, long position, int length) {
    leafMap.put(nodeId, (position << 32) | length);
    leafDocumentSources.put(nodeId, documentId);
    nodeTypeMap.put(nodeId, null);
  }

  public int getSourceDocumentPosition(String nodeId) {
    final Long encodedLeaf = leafMap.get(nodeId);
    if (encodedLeaf != null) {
      return (int) (encodedLeaf >> 32);
    } else {
      return -1;
    }
  }

  private void addParentNode(String nodeId, MsgPackNodeType nodeType) {
    nodeTypeMap.put(nodeId, nodeType);
    if (!nodeChildsMap.containsKey(nodeId)) {
      nodeChildsMap.put(nodeId, new LinkedHashSet<>());
    }
  }

  public void addMapNode(String nodeId) {
    if (isLeaf(nodeId)) {
      leafMap.remove(nodeId);
    }
    addParentNode(nodeId, MsgPackNodeType.MAP_NODE);
  }

  public void addArrayNode(String nodeId) {
    addParentNode(nodeId, MsgPackNodeType.ARRAY_NODE);
  }

  public void removeContainerNode(String nodeId) {
    nodeTypeMap.remove(nodeId);
    nodeChildsMap.remove(nodeId);
  }

  public void addChildToNode(String childName, String parentId) {
    nodeChildsMap.get(parentId).add(childName);
  }

  public boolean isLeaf(String nodeId) {
    return leafMap.containsKey(nodeId);
  }

  public boolean isArrayNode(String nodeId) {
    final MsgPackNodeType msgPackNodeType = nodeTypeMap.get(nodeId);
    return msgPackNodeType != null && msgPackNodeType == MsgPackNodeType.ARRAY_NODE;
  }

  public boolean isMapNode(String nodeId) {
    final MsgPackNodeType msgPackNodeType = nodeTypeMap.get(nodeId);
    return msgPackNodeType != null && msgPackNodeType == MsgPackNodeType.MAP_NODE;
  }

  public DirectBuffer getDocument(String nodeId) {
    final int sourceDocId = leafDocumentSources.getValue(nodeId);

    if (sourceDocId >= 0) {
      return documents[sourceDocId];
    } else {
      return null;
    }
  }

  public void writeLeafMapping(MsgPackWriter writer, String leafId) {
    final long mapping = leafMap.get(leafId);
    final int position = (int) (mapping >> 32);
    final int length = (int) mapping;

    final int documentId = leafDocumentSources.getValue(leafId);
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
      if (!(other.nodeTypeMap.get(key) == MsgPackNodeType.MAP_NODE
          && nodeType == MsgPackNodeType.ARRAY_NODE)) {
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
}
