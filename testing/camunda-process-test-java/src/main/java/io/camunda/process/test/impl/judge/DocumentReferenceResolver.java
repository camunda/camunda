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
import io.camunda.client.api.fetch.DocumentContentGetRequest;
import io.camunda.process.test.api.judge.ResolvedDocument;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
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
 * <p>Per-document download failures are recorded in the returned {@link ResolvedDocument} list with
 * {@link ResolvedDocument#getErrorMessage()} set, so the judge can be told about the gap rather
 * than silently dropping the reference.
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
   * content in parallel.
   *
   * @param variableJson the raw JSON value of the asserted variable, may be any JSON shape
   * @return the resolved documents in encounter order; may be empty if no references are found or
   *     the JSON cannot be parsed
   */
  public List<ResolvedDocument> resolve(final String variableJson) {
    final List<JsonNode> references = findReferences(variableJson);
    if (references.isEmpty()) {
      return Collections.emptyList();
    }
    LOG.debug("Found {} Camunda document reference(s) in variable", references.size());
    return downloadAll(references);
  }

  // --- reference detection ---

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
      node.fields().forEachRemaining(entry -> collectReferences(entry.getValue(), out));
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

  // --- parallel download ---

  private List<ResolvedDocument> downloadAll(final List<JsonNode> references) {
    final List<CompletableFuture<ResolvedDocument>> futures =
        references.stream().map(this::downloadAsync).collect(Collectors.toList());
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    return futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
  }

  private CompletableFuture<ResolvedDocument> downloadAsync(final JsonNode reference) {
    final ReferenceMetadata meta = ReferenceMetadata.from(reference);
    if (meta.documentId == null || meta.documentId.isEmpty()) {
      LOG.warn("Skipping Camunda document reference without documentId");
      return CompletableFuture.completedFuture(
          ResolvedDocumentImpl.failed(null, meta.fileName, meta.contentType, "missing documentId"));
    }
    return CompletableFuture.supplyAsync(() -> download(meta));
  }

  private ResolvedDocument download(final ReferenceMetadata meta) {
    try (final InputStream stream = openStream(meta)) {
      final byte[] data = readAllBytes(stream);
      return ResolvedDocumentImpl.resolved(meta.documentId, meta.fileName, meta.contentType, data);
    } catch (final Exception e) {
      LOG.warn(
          "Failed to download Camunda document '{}' for judge enrichment: {}",
          meta.documentId,
          e.getMessage());
      return ResolvedDocumentImpl.failed(
          meta.documentId, meta.fileName, meta.contentType, e.getMessage());
    }
  }

  private InputStream openStream(final ReferenceMetadata meta) {
    DocumentContentGetRequest request = client.newDocumentContentGetRequest(meta.documentId);
    if (meta.storeId != null) {
      request = request.storeId(meta.storeId);
    }
    if (meta.contentHash != null) {
      request = request.contentHash(meta.contentHash);
    }
    return request.send().join();
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

  // --- helpers ---

  private static String textOrNull(final JsonNode node, final String field) {
    final JsonNode value = node.get(field);
    return value == null || value.isNull() || !value.isTextual() ? null : value.asText();
  }

  private static final class ReferenceMetadata {
    final String documentId;
    final String storeId;
    final String contentHash;
    final String fileName;
    final String contentType;

    private ReferenceMetadata(
        final String documentId,
        final String storeId,
        final String contentHash,
        final String fileName,
        final String contentType) {
      this.documentId = documentId;
      this.storeId = storeId;
      this.contentHash = contentHash;
      this.fileName = fileName;
      this.contentType = contentType;
    }

    static ReferenceMetadata from(final JsonNode reference) {
      final JsonNode metadata = reference.path("metadata");
      return new ReferenceMetadata(
          textOrNull(reference, "documentId"),
          textOrNull(reference, "storeId"),
          textOrNull(reference, "contentHash"),
          textOrNull(metadata, "fileName"),
          textOrNull(metadata, "contentType"));
    }
  }
}
