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
package io.camunda.client.impl.search.request;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.http.JsonResponseTransformer;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.client5.http.config.RequestConfig;

/**
 * Before 8.10, the {@code roleId}/{@code mappingRuleId} search filters only accepted a plain
 * string. 8.10 upgraded them to the same advanced filter shape as every other identifier filter, so
 * the client's plain-equality convenience methods (e.g. {@code roleId("foo")}) now send {@code
 * {"$eq": "foo"}} instead of a bare string. A cluster running 8.9 or earlier rejects that object
 * with a 400, because its schema still declares the field as a plain string.
 *
 * <p>This makes a single search request resilient to that: it sends the modern shape first and,
 * only on the specific 400 that signals the pre-8.10 shape, retries with the field rewritten as a
 * bare string. Once a cluster's behavior is observed, it is remembered for the lifetime of this
 * instance so later searches on the same client skip the doomed first attempt.
 */
public final class LegacyIdEqualityFilterFallback {

  private final String fieldName;
  private final String parseErrorDetail;
  private final AtomicReference<Boolean> legacyFormatRequired = new AtomicReference<>();

  public LegacyIdEqualityFilterFallback(final String fieldName) {
    this.fieldName = fieldName;
    parseErrorDetail = "Request property [filter." + fieldName + "] cannot be parsed";
  }

  public <HttpT, RespT> void post(
      final HttpClient httpClient,
      final String path,
      final Object request,
      final JsonMapper jsonMapper,
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {
    if (Boolean.TRUE.equals(legacyFormatRequired.get())) {
      final String legacyBody = buildLegacyBody(jsonMapper, request);
      httpClient.post(
          path,
          legacyBody != null ? legacyBody : jsonMapper.toJson(request),
          requestConfig,
          responseType,
          transformer,
          result);
      return;
    }

    final HttpCamundaFuture<RespT> firstAttempt = new HttpCamundaFuture<>();
    httpClient.post(
        path, jsonMapper.toJson(request), requestConfig, responseType, transformer, firstAttempt);
    firstAttempt.whenComplete(
        (response, error) -> {
          if (error == null) {
            legacyFormatRequired.compareAndSet(null, false);
            result.complete(response);
            return;
          }
          final String legacyBody =
              isLegacyFormatError(error) ? buildLegacyBody(jsonMapper, request) : null;
          if (legacyBody == null) {
            result.completeExceptionally(error);
            return;
          }
          legacyFormatRequired.set(true);
          httpClient.post(path, legacyBody, requestConfig, responseType, transformer, result);
        });
  }

  private boolean isLegacyFormatError(final Throwable error) {
    return error instanceof ProblemException
        && ((ProblemException) error).details() != null
        && parseErrorDetail.equals(((ProblemException) error).details().getDetail());
  }

  @SuppressWarnings("unchecked")
  private String buildLegacyBody(final JsonMapper jsonMapper, final Object request) {
    final Map<String, Object> root = jsonMapper.fromJsonAsMap(jsonMapper.toJson(request));
    final Object filterValue = root.get("filter");
    if (!(filterValue instanceof Map)) {
      return null;
    }
    final Map<String, Object> filter = (Map<String, Object>) filterValue;
    final Object fieldValue = filter.get(fieldName);
    if (!(fieldValue instanceof Map)) {
      return null;
    }
    final Map<?, ?> advanced = (Map<?, ?>) fieldValue;
    final Object eqValue = advanced.get("$eq");
    if (!(eqValue instanceof String) || hasOtherOperatorsSet(advanced)) {
      return null;
    }
    filter.put(fieldName, eqValue);
    return jsonMapper.toJson(root);
  }

  /**
   * Other {@code StringFilterProperty} operators (e.g. {@code $in}, {@code $notIn}) are unrelated
   * generated fields that default to an empty list rather than {@code null}, so they show up in
   * every request regardless of whether the caller set them. Only a genuinely-set operator besides
   * {@code $eq} rules out the plain-string fallback.
   */
  private boolean hasOtherOperatorsSet(final Map<?, ?> advanced) {
    return advanced.entrySet().stream()
        .anyMatch(
            entry ->
                !"$eq".equals(entry.getKey())
                    && entry.getValue() != null
                    && !(entry.getValue() instanceof Collection
                        && ((Collection<?>) entry.getValue()).isEmpty()));
  }
}
