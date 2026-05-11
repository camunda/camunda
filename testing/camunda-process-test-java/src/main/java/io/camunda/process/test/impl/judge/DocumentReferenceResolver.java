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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.impl.response.DocumentReferenceResponseImpl;
import io.camunda.process.test.api.judge.ResolvedDocument;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects Camunda document references inside a variable's JSON value and downloads their binary
 * content via the {@link CamundaClient}.
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
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final CamundaClient client;

  public DocumentReferenceResolver(final CamundaClient client) {
    this.client = client;
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
    final List<JsonNode> referenceNodes = findReferences(variableJson);
    if (referenceNodes.isEmpty()) {
      return Collections.emptyList();
    }
    LOG.debug("Found {} Camunda document reference(s) in variable", referenceNodes.size());
    final List<ResolvedDocument> resolved = new ArrayList<>(referenceNodes.size());
    for (final JsonNode node : referenceNodes) {
      resolved.add(download(parseReference(node)));
    }
    return resolved;
  }

  private static List<JsonNode> findReferences(final String variableJson) {
    final JsonNode root = parseJsonOrNull(variableJson);
    if (root == null) {
      return Collections.emptyList();
    }
    final List<JsonNode> references = new ArrayList<>();
    collectReferences(root, references);
    return references;
  }

  private static JsonNode parseJsonOrNull(final String variableJson) {
    if (variableJson == null || variableJson.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.readTree(variableJson);
    } catch (final IOException e) {
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

  private static DocumentReferenceResponse parseReference(final JsonNode referenceNode) {
    final DocumentReferenceResponse reference;
    try {
      reference = OBJECT_MAPPER.treeToValue(referenceNode, DocumentReferenceResponseImpl.class);
    } catch (final IOException e) {
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
    try (final InputStream stream = client.newDocumentContentGetRequest(reference).send().join()) {
      return new ResolvedDocumentImpl(reference, readAllBytes(stream));
    } catch (final Exception e) {
      throw new IllegalStateException(
          "Failed to download Camunda document '"
              + reference.getDocumentId()
              + "' for judge enrichment: "
              + e.getMessage(),
          e);
    }
  }

  private static byte[] readAllBytes(final InputStream stream) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final byte[] buffer = new byte[8192];
    int read;
    while ((read = stream.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }
}
