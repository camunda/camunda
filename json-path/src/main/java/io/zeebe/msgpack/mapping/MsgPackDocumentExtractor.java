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

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathToken;
import io.zeebe.msgpack.jsonpath.JsonPathTokenVisitor;
import io.zeebe.msgpack.jsonpath.JsonPathTokenizer;
import io.zeebe.msgpack.query.MsgPackQueryExecutor;
import io.zeebe.msgpack.query.MsgPackTraverser;
import org.agrona.DirectBuffer;

/**
 * Represents an message pack document extractor.
 *
 * <p>The extractor can wrap a message pack document, which is stored in a {@link DirectBuffer} and
 * extract parts of this wrapped document with help of the given {@link Mapping} objects. The
 * extracted parts are stored in a {@link MsgPackTree} object, which is returned after calling
 * {@link #extract(Mapping...)}.
 *
 * <p>It is also possible that the extractor wraps an already existing {@link MsgPackTree} object
 * and a message pack document on which the extracting should be done. The extracted parts are
 * stored in the wrapped tree. This means nodes can be added or replaced in the wrapped message pack
 * tree.
 *
 * <p>A {@link Mapping} consist of a source json query and a target json path mapping. The source
 * json query must match on the wrapped message pack document. The matching value will be stored in
 * the resulting {@link MsgPackTree} object, the tree is either the wrapped tree or a tree which
 * only consists of the extracted values.
 *
 * <p>The resulting {@link MsgPackTree} consist of nodes for each part in the target json path
 * mapping and leafs, which corresponds to the leaf in the target json path mapping. These leafs
 * contains the values of the matched source json query, form the wrapped message pack document.
 *
 * <p>Example:
 *
 * <pre>
 * {@code Wrapped document:
 *  {
 *     "sourceObject":{
 *         "foo":"bar"
 *     },
 *     "value1":1
 *  }
 * }
 * </pre>
 *
 * <pre>
 * {@code Mappings:
 *  $.sourceObject -> $.targetObject.value1
 *  $.value1 -> $.newValue1
 * }
 * </pre>
 *
 * <pre>{@code
 * Then the resulting tree will look like the following:
 *
 *           $
 *        /     \
 *  newValue1   targetObject
 *    |           \
 *    1           value1
 *                 \
 *                 {"foo":"bar"}
 * }</pre>
 */
public final class MsgPackDocumentExtractor {
  public static final String EXCEPTION_MSG_MAPPING_DOES_NOT_MATCH = "No data found for query %s.";
  public static final String EXCEPTION_MSG_MAPPING_HAS_MORE_THAN_ONE_MATCHING_SOURCE =
      "JSON path mapping has more than one matching source.";

  /**
   * Holds the reference of the msg pack document tree, on which the extracted values are stored.
   * Could either hold a reference of a wrapped message pack document tree or of the {@link
   * #extractDocumentTree}, which is used if a new document tree should be created from an
   * extraction of an document.
   */
  private MsgPackTree documentTreeReference;

  /**
   * This message pack document tree will be used if only a messag pack document is wrapped and
   * parts of the document should be extracted.
   */
  private final MsgPackTree extractDocumentTree = new MsgPackTree();

  private final MsgPackTraverser traverser = new MsgPackTraverser();
  private final MsgPackQueryExecutor queryExecutor = new MsgPackQueryExecutor();
  private final JsonPathTokenizer tokenizer = new JsonPathTokenizer();
  private final TargetPathVisitor targetPathVisitor = new TargetPathVisitor();

  /**
   * Wraps a existing message pack document tree and a message pack document, on which the
   * extracting should be executed.
   *
   * @param existingDocumentTree the tree on which the extracted parts are stored
   * @param extractDocument the document on which parts should be extracted
   */
  public void wrap(MsgPackTree existingDocumentTree, DirectBuffer extractDocument) {
    documentTreeReference = existingDocumentTree;
    documentTreeReference.setExtractDocument(extractDocument);
    traverser.wrap(extractDocument, 0, extractDocument.capacity());
  }

  public void wrap(DirectBuffer document) {
    documentTreeReference = extractDocumentTree;
    documentTreeReference.setExtractDocument(document);
    traverser.wrap(document, 0, document.capacity());
  }

  public MsgPackTree extract(Mapping... mappings) {
    for (Mapping mapping : mappings) {
      targetPathVisitor.reset(mapping);
      final DirectBuffer targetQueryString = mapping.getTargetQueryBuffer();
      tokenizer.tokenize(targetQueryString, 0, targetQueryString.capacity(), targetPathVisitor);
    }
    return documentTreeReference;
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
  private String createParentRelation(String parentId, String nodeName) {
    final String nodeId;

    if (parentId.isEmpty()) {
      nodeId = nodeName;
    } else {
      final boolean isIndex = isIndex(nodeName);

      if (isIndex) {
        if (!documentTreeReference.isMapNode(parentId)) {
          documentTreeReference.addArrayNode(parentId);
        }
      } else {
        documentTreeReference.addMapNode(parentId);
      }
      nodeId = construct(parentId, nodeName);
      documentTreeReference.addChildToNode(nodeName, parentId);
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

  /**
   * Executes the given json path query. The matching result is available in the query executor
   * object.
   *
   * @param jsonPathQuery the query which should be executed
   */
  private void executeLeafMapping(JsonPathQuery jsonPathQuery) {
    queryExecutor.init(jsonPathQuery.getFilters(), jsonPathQuery.getFilterInstances());
    traverser.traverse(queryExecutor);

    if (queryExecutor.numResults() == 1) {
      queryExecutor.moveToResult(0);
    } else if (queryExecutor.numResults() == 0) {
      final DirectBuffer expression = jsonPathQuery.getExpression();
      throw new MappingException(
          String.format(
              EXCEPTION_MSG_MAPPING_DOES_NOT_MATCH,
              expression.getStringWithoutLengthUtf8(0, expression.capacity())));
    } else {
      throw new IllegalStateException(EXCEPTION_MSG_MAPPING_HAS_MORE_THAN_ONE_MATCHING_SOURCE);
    }
    traverser.reset();
  }

  private final class TargetPathVisitor implements JsonPathTokenVisitor {
    private String nodeId;
    private String parentId;
    private Mapping mapping;

    void reset(Mapping mapping) {
      nodeId = "";
      parentId = "";
      this.mapping = mapping;
    }

    @Override
    public void visit(
        JsonPathToken type, DirectBuffer valueBuffer, int valueOffset, int valueLength) {
      if (type == JsonPathToken.LITERAL || type == JsonPathToken.ROOT_OBJECT) {
        final String nodeName = valueBuffer.getStringWithoutLengthUtf8(valueOffset, valueLength);
        nodeId = createParentRelation(parentId, nodeName);
        parentId = nodeId;
      } else if (type == JsonPathToken.END_INPUT) {
        executeLeafMapping(mapping.getSource());
        documentTreeReference.addLeafNode(
            nodeId, queryExecutor.currentResultPosition(), queryExecutor.currentResultLength());
      }
    }
  }

  public void clear() {
    if (documentTreeReference != null) {
      this.documentTreeReference.clear();
    }
    this.traverser.reset();
  }
}
