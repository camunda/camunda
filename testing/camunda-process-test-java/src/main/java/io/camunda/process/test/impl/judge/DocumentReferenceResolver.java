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
 * an error message set, so the judge can be told about the gap rather than silently dropping the
 * reference.
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
    final List<JsonNode> referenceNodes = findReferences(variableJson);
    if (referenceNodes.isEmpty()) {
      return Collections.emptyList();
    }
    LOG.debug("Found {} Camunda document reference(s) in variable", referenceNodes.size());
    return downloadAll(referenceNodes);
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

  // Each download is dispatched to the common ForkJoinPool and blocks that worker on
  // request.send().join() plus the stream read. This is acceptable for the test-scope
  // fan-out expected here (a handful of documents per asserted variable); revisit if
  // we ever need to resolve large batches.
  private List<ResolvedDocument> downloadAll(final List<JsonNode> referenceNodes) {
    final List<CompletableFuture<ResolvedDocument>> futures =
        referenceNodes.stream().map(this::downloadAsync).collect(Collectors.toList());
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    return futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
  }

  private CompletableFuture<ResolvedDocument> downloadAsync(final JsonNode referenceNode) {
    final DocumentReferenceResponse reference;
    try {
      reference = OBJECT_MAPPER.treeToValue(referenceNode, DocumentReferenceResponseImpl.class);
    } catch (final IOException e) {
      LOG.warn("Skipping unparseable Camunda document reference: {}", e.getMessage());
      return CompletableFuture.completedFuture(ResolvedDocumentImpl.failed(null, e.getMessage()));
    }
    if (reference.getDocumentId() == null || reference.getDocumentId().isEmpty()) {
      LOG.warn("Skipping Camunda document reference without documentId");
      return CompletableFuture.completedFuture(
          ResolvedDocumentImpl.failed(reference, "missing documentId"));
    }
    return CompletableFuture.supplyAsync(() -> download(reference));
  }

  private ResolvedDocument download(final DocumentReferenceResponse reference) {
    try (final InputStream stream = client.newDocumentContentGetRequest(reference).send().join()) {
      return ResolvedDocumentImpl.resolved(reference, readAllBytes(stream));
    } catch (final Exception e) {
      LOG.warn(
          "Failed to download Camunda document '{}' for judge enrichment: {}",
          reference.getDocumentId(),
          e.getMessage());
      return ResolvedDocumentImpl.failed(reference, e.getMessage());
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
