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
package io.camunda.process.test.impl.judge;

import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.impl.response.DocumentReferenceResponseImpl;
import io.camunda.client.protocol.rest.DocumentReference;
import io.camunda.client.protocol.rest.DocumentReference.CamundaDocumentTypeEnum;
import io.camunda.process.test.api.judge.ResolvedDocument;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.assertions.util.CamundaAssertJsonMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects Camunda document references inside a variable's JSON value and downloads their binary
 * content via the {@link CamundaDataSource}.
 *
 * <p>Resolution is fail-fast: any download or parse error throws an {@link IllegalStateException}
 * so the surrounding assertion surfaces the problem immediately instead of letting the judge
 * silently reason without the document content.
 */
public final class DocumentReferenceResolver {

  // Discriminator field/value identifying a Camunda document reference in variable JSON.
  static final String DOCUMENT_TYPE_FIELD = DocumentReference.JSON_PROPERTY_CAMUNDA_DOCUMENT_TYPE;
  static final String DOCUMENT_TYPE_VALUE = CamundaDocumentTypeEnum.CAMUNDA.getValue();

  private static final Logger LOG = LoggerFactory.getLogger(DocumentReferenceResolver.class);

  private final CamundaDataSource dataSource;
  private final CamundaAssertJsonMapper jsonMapper;

  public DocumentReferenceResolver(
      final CamundaDataSource dataSource, final CamundaAssertJsonMapper jsonMapper) {
    this.dataSource = dataSource;
    this.jsonMapper = jsonMapper;
  }

  /**
   * Parses the given variable JSON, locates Camunda document references, and downloads their
   * content sequentially. References are detected at any depth (top-level, nested objects, arrays)
   * and de-duplicated by {@code (documentId, storeId)} before download.
   *
   * @throws IllegalStateException if any reference cannot be parsed or its content cannot be
   *     downloaded
   */
  public List<ResolvedDocument> resolve(final String variableJson) {
    final List<Map<String, Object>> referenceNodes = findReferences(variableJson);
    if (referenceNodes.isEmpty()) {
      return Collections.emptyList();
    }
    LOG.debug("Found {} Camunda document reference(s) in variable", referenceNodes.size());
    final Map<DocumentKey, ResolvedDocument> seen = new LinkedHashMap<>();
    for (final Map<String, Object> node : referenceNodes) {
      final DocumentReferenceResponse ref = parseReference(node);
      final DocumentKey key = new DocumentKey(ref.getDocumentId(), ref.getStoreId());
      if (!seen.containsKey(key)) {
        seen.put(key, download(ref));
      }
    }
    return new ArrayList<>(seen.values());
  }

  private List<Map<String, Object>> findReferences(final String variableJson) {
    final Object root = parseJsonOrNull(variableJson);
    if (root == null) {
      return Collections.emptyList();
    }
    final List<Map<String, Object>> references = new ArrayList<>();
    collectReferences(root, references);
    return references;
  }

  private Object parseJsonOrNull(final String variableJson) {
    if (variableJson == null || variableJson.isEmpty()) {
      return null;
    }
    try {
      return jsonMapper.readJson(variableJson);
    } catch (final CamundaAssertJsonMapper.JsonMappingException e) {
      LOG.debug(
          "Variable value is not valid JSON, skipping document resolution: {}", e.getMessage());
      return null;
    }
  }

  private static void collectReferences(final Object node, final List<Map<String, Object>> out) {
    if (node instanceof List) {
      ((List<?>) node).forEach(element -> collectReferences(element, out));
      return;
    }
    if (!(node instanceof Map)) {
      return;
    }
    @SuppressWarnings("unchecked")
    final Map<String, Object> objectNode = (Map<String, Object>) node;
    if (isDocumentReference(objectNode)) {
      out.add(objectNode);
      return;
    }
    objectNode.values().forEach(value -> collectReferences(value, out));
  }

  private static boolean isDocumentReference(final Map<String, Object> node) {
    return DOCUMENT_TYPE_VALUE.equals(node.get(DOCUMENT_TYPE_FIELD));
  }

  private DocumentReferenceResponse parseReference(final Map<String, Object> referenceNode) {
    final DocumentReferenceResponse reference;
    try {
      // Deserialize the protocol type rather than DocumentReferenceResponseImpl: the latter is
      // only constructible through a Jackson 2 @JsonDeserialize hook, which a Jackson 3 mapper
      // ignores. DocumentReference carries plain jackson-annotations, understood by both.
      reference =
          new DocumentReferenceResponseImpl(
              jsonMapper.readJson(jsonMapper.toJson(referenceNode), DocumentReference.class));
    } catch (final CamundaAssertJsonMapper.JsonMappingException e) {
      throw new IllegalStateException(
          "Failed to parse Camunda document reference: " + e.getMessage(), e);
    }
    if (reference.getDocumentId() == null || reference.getDocumentId().isEmpty()) {
      throw new IllegalStateException(
          "Camunda document reference is missing documentId; cannot resolve content for judge");
    }
    return reference;
  }

  private ResolvedDocument download(final DocumentReferenceResponse reference) {
    return new ResolvedDocumentImpl(reference, dataSource.getDocumentContent(reference));
  }

  private static final class DocumentKey {
    private final String documentId;
    private final String storeId;

    DocumentKey(final String documentId, final String storeId) {
      this.documentId = documentId;
      this.storeId = storeId;
    }

    @Override
    public int hashCode() {
      return Objects.hash(documentId, storeId);
    }

    @Override
    public boolean equals(final Object o) {
      if (!(o instanceof DocumentKey)) {
        return false;
      }
      final DocumentKey other = (DocumentKey) o;
      return Objects.equals(documentId, other.documentId) && Objects.equals(storeId, other.storeId);
    }
  }
}
