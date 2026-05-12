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

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.client.api.response.DocumentReferenceResponse;
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
 * <p>A document reference is any JSON object containing the discriminator field {@code
 * "camunda.document.type": "camunda"}. References are detected at any depth (top-level, nested
 * objects, arrays).
 *
 * <p>Resolution is fail-fast: any download or parse error throws an {@link IllegalStateException}
 * so the surrounding assertion surfaces the problem immediately instead of letting the judge
 * silently reason without the document content.
 */
public final class DocumentReferenceResolver {

  static final String DOCUMENT_TYPE_FIELD = "camunda.document.type";
  static final String DOCUMENT_TYPE_VALUE = "camunda";

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
   * content sequentially.
   *
   * @param variableJson the raw JSON value of the asserted variable, may be any JSON shape
   * @return the resolved documents in encounter order; may be empty if no references are found or
   *     the JSON cannot be parsed
   * @throws IllegalStateException if any reference cannot be parsed or its content cannot be
   *     downloaded
   */
  public List<ResolvedDocument> resolve(final String variableJson) {
    final List<JsonNode> referenceNodes = findReferences(variableJson, jsonMapper);
    if (referenceNodes.isEmpty()) {
      return Collections.emptyList();
    }
    LOG.debug("Found {} Camunda document reference(s) in variable", referenceNodes.size());
    final Map<DocumentKey, ResolvedDocument> seen = new LinkedHashMap<>();
    for (final JsonNode node : referenceNodes) {
      final DocumentReferenceResponse ref = parseReference(node, jsonMapper);
      final DocumentKey key = new DocumentKey(ref.getDocumentId(), ref.getStoreId());
      if (!seen.containsKey(key)) {
        seen.put(key, download(ref));
      }
    }
    return new ArrayList<>(seen.values());
  }

  private static List<JsonNode> findReferences(
      final String variableJson, final CamundaAssertJsonMapper jsonMapper) {
    final JsonNode root = parseJsonOrNull(variableJson, jsonMapper);
    if (root == null) {
      return Collections.emptyList();
    }
    final List<JsonNode> references = new ArrayList<>();
    collectReferences(root, references);
    return references;
  }

  private static JsonNode parseJsonOrNull(
      final String variableJson, final CamundaAssertJsonMapper jsonMapper) {
    if (variableJson == null || variableJson.isEmpty()) {
      return null;
    }
    try {
      return jsonMapper.readJson(variableJson, JsonNode.class);
    } catch (final CamundaAssertJsonMapper.JsonMappingException e) {
      LOG.debug(
          "Variable value is not valid JSON, skipping document resolution: {}", e.getMessage());
      return null;
    }
  }

  private static void collectReferences(final JsonNode node, final List<JsonNode> out) {
    if (node == null || node.isMissingNode()) {
      return;
    }
    if (isDocumentReference(node)) {
      out.add(node);
      return;
    }
    if (node.isArray()) {
      node.forEach(element -> collectReferences(element, out));
    } else if (node.isObject()) {
      node.fieldNames().forEachRemaining(fieldName -> collectReferences(node.get(fieldName), out));
    }
  }

  private static boolean isDocumentReference(final JsonNode node) {
    if (!node.isObject()) {
      return false;
    }
    final JsonNode typeNode = node.get(DOCUMENT_TYPE_FIELD);
    return typeNode != null
        && typeNode.isTextual()
        && DOCUMENT_TYPE_VALUE.equals(typeNode.asText());
  }

  private static DocumentReferenceResponse parseReference(
      final JsonNode referenceNode, final CamundaAssertJsonMapper jsonMapper) {
    final DocumentReferenceResponse reference;
    try {
      reference = jsonMapper.readJson(referenceNode.toString(), DocumentReferenceDto.class);
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
    public boolean equals(final Object o) {
      if (!(o instanceof DocumentKey)) {
        return false;
      }
      final DocumentKey other = (DocumentKey) o;
      return Objects.equals(documentId, other.documentId)
          && Objects.equals(storeId, other.storeId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(documentId, storeId);
    }
  }
}
