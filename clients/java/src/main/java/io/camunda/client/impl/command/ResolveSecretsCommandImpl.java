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
package io.camunda.client.impl.command;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.ResolveSecretsCommandStep1;
import io.camunda.client.api.response.ResolveSecretsResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.ResolveSecretsResponseImpl;
import io.camunda.client.protocol.rest.SecretResolveRequest;
import io.camunda.client.protocol.rest.SecretResolveResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class ResolveSecretsCommandImpl implements ResolveSecretsCommandStep1 {

  private final SecretResolveRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public ResolveSecretsCommandImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new SecretResolveRequest();
  }

  @Override
  public ResolveSecretsCommandStep1 references(final List<String> references) {
    ArgumentUtil.ensureNotNull("references", references);
    // Null entries are rejected here because the cluster rejects the whole request for them, so
    // sending one would fail the batch anyway. Everything else — the batch size, the reference
    // length, duplicates and the reference format, an empty reference included — is owned by the
    // cluster: a malformed reference comes back as a resolution error, not as an exception, and
    // must therefore not fail the references alongside it.
    references.forEach(reference -> ArgumentUtil.ensureNotNull("reference", reference));
    request.setReferences(new ArrayList<>(references));
    return this;
  }

  @Override
  public ResolveSecretsCommandStep1 references(final String... references) {
    ArgumentUtil.ensureNotNull("references", references);
    return references(Arrays.asList(references));
  }

  @Override
  public ResolveSecretsCommandStep1 reference(final String reference) {
    ArgumentUtil.ensureNotNull("reference", reference);
    request.addReferencesItem(reference);
    return this;
  }

  @Override
  public ResolveSecretsCommandStep1 requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<ResolveSecretsResponse> send() {
    ArgumentUtil.ensureNotNullOrEmpty("references", request.getReferences());
    // Deduplicated because the cluster deduplicates before resolving, so a duplicate reference
    // comes back once. The response needs the requested references to tell a fully resolved batch
    // apart from one whose references were dropped on the way back.
    final Set<String> requestedReferences = new LinkedHashSet<>(request.getReferences());
    final HttpCamundaFuture<ResolveSecretsResponse> result = new HttpCamundaFuture<>();
    final String path = "/secrets/resolve";
    // Sent as a sensitive response so that a response the client cannot make sense of is reported
    // without echoing the body: the resolved values are only kept out of logs by
    // ResolveSecretsResponseImpl, whereas the wire representation prints them.
    httpClient.postWithSensitiveResponse(
        path,
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        SecretResolveResult.class,
        response -> new ResolveSecretsResponseImpl(response, requestedReferences),
        result);
    return result;
  }
}
