/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.util.opensearch;

import io.camunda.operate.exceptions.PersistenceException;
import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpensearchRequestValidator {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchRequestValidator.class);

  public static BulkRequest validateIndices(
      final BulkRequest bulkRequest, final boolean ignoreNullIndex, final JsonpMapper jsonpMapper)
      throws PersistenceException {
    final List<BulkOperation> invalidRequests =
        bulkRequest.operations().stream()
            .filter(OpensearchRequestValidator::isIndexMissing)
            .toList();
    if (invalidRequests.isEmpty()) {
      return bulkRequest;
    }

    final String requestsError =
        invalidRequests.stream()
            .map(r -> "- request: " + toJsonString(r, jsonpMapper))
            .collect(Collectors.joining(System.lineSeparator()));
    if (ignoreNullIndex) {
      LOGGER.warn(
          String.format(
              "Bulk request has %d requests with missing index. Ignoring invalid requests:%s%s",
              invalidRequests.size(), System.lineSeparator(), requestsError));
      final BulkRequest.Builder builder = new BulkRequest.Builder();
      final List<BulkOperation> operations =
          bulkRequest.operations().stream().filter(o -> !isIndexMissing(o)).toList();
      final BulkRequest newBulkRequest =
          builder
              .index(bulkRequest.index())
              .operations(operations)
              .pipeline(bulkRequest.pipeline())
              .refresh(bulkRequest.refresh())
              .routing(bulkRequest.routing())
              .requireAlias(bulkRequest.requireAlias())
              .source(bulkRequest.source())
              .sourceExcludes(bulkRequest.sourceExcludes())
              .sourceIncludes(bulkRequest.sourceIncludes())
              .timeout(bulkRequest.timeout())
              .waitForActiveShards(bulkRequest.waitForActiveShards())
              .build();
      return newBulkRequest;
    }

    throw new PersistenceException(
        String.format(
            "Bulk request has %d requests with missing index:%s%s",
            invalidRequests.size(), System.lineSeparator(), requestsError));
  }

  public static boolean isIndexMissing(final BulkOperation operation) {
    String index = null;
    if (operation.isIndex()) {
      index = operation.index().index();
    } else if (operation.isCreate()) {
      index = operation.create().index();
    } else if (operation.isUpdate()) {
      index = operation.update().index();
    } else if (operation.isDelete()) {
      index = operation.delete().index();
    }
    final boolean isMissing = (index == null) || index.isEmpty();
    return isMissing;
  }

  public static String toJsonString(final BulkOperation operation, final JsonpMapper jsonpMapper) {
    final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try (final JsonGenerator generator =
        jsonpMapper.jsonProvider().createGenerator(byteArrayOutputStream)) {
      operation.serialize(generator, new JsonbJsonpMapper());
    }
    return byteArrayOutputStream.toString(StandardCharsets.UTF_8);
  }
}
