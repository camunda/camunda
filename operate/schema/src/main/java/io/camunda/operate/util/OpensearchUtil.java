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
package io.camunda.operate.util;

import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.ReindexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OpensearchUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchUtil.class);

  private OpensearchUtil() {
    // Utility class, no instantiation
  }

  public static <T> Either<Throwable, Long> handleResponse(
      final T response, final Throwable error, final String sourceIndexName) {
    final String operation = response instanceof ReindexResponse ? "reindex" : "deleteByQuery";
    if (error != null) {
      final var message =
          String.format(
              "Exception occurred, while performing operation %s on source index %s. Error: %s",
              operation, sourceIndexName, error.getMessage());
      return Either.left(new ArchiverException(message, error));
    }

    final var bulkFailures =
        response instanceof ReindexResponse
            ? ((ReindexResponse) response).failures()
            : ((DeleteByQueryResponse) response).failures();

    if (!bulkFailures.isEmpty()) {
      LOGGER.error(
          "Failures occurred when performing operation: {} on source index {}. See details below.",
          operation,
          sourceIndexName);
      bulkFailures.forEach(f -> LOGGER.error(f.toString()));
      return Either.left(new ArchiverException(String.format("Operation %s failed", operation)));
    }

    LOGGER.debug("Operation {} succeeded on source index {}", operation, sourceIndexName);
    return Either.right(
        response instanceof ReindexResponse
            ? ((ReindexResponse) response).total()
            : ((DeleteByQueryResponse) response).total());
  }

  public static <T> CompletableFuture<SearchResponse<T>> searchAsync(
      final SearchRequest request, final Class<T> tClass, final OpenSearchAsyncClient client) {
    try {
      return client.search(request, tClass);
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
  }

  public static CompletableFuture<ReindexResponse> reindexAsync(
      final ReindexRequest request, final OpenSearchAsyncClient client) {
    try {
      return client.reindex(request);
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
  }

  public static CompletableFuture<DeleteByQueryResponse> deleteAsync(
      final DeleteByQueryRequest request, final OpenSearchAsyncClient client) {
    try {
      return client.deleteByQuery(request);
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
  }
}
