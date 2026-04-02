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
package io.camunda.client.impl.http;

import io.camunda.client.CredentialsProvider;
import java.io.IOException;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;

/**
 * An Apache HttpClient 5 execution chain interceptor that applies OAuth/auth credentials to every
 * outgoing HTTP request at <strong>wire time</strong>, just before the request is dispatched to the
 * server.
 *
 * <p>This is critical for long-lived async pipelines: if requests are queued inside the Apache
 * {@code CloseableHttpAsyncClient} (due to connection-pool limits, backpressure, or I/O thread
 * contention), attaching the token at queue time would cause the JWT to expire before the request
 * actually reaches the server. By deferring credential injection to the execution chain, every
 * request always carries the freshest available token.
 */
final class CredentialsInterceptor implements AsyncExecChainHandler {

  private final CredentialsProvider credentialsProvider;

  CredentialsInterceptor(final CredentialsProvider credentialsProvider) {
    this.credentialsProvider = credentialsProvider;
  }

  @Override
  public void execute(
      final HttpRequest request,
      final AsyncEntityProducer entityProducer,
      final AsyncExecChain.Scope scope,
      final AsyncExecChain chain,
      final AsyncExecCallback asyncExecCallback)
      throws HttpException, IOException {
    credentialsProvider.applyCredentials(request::setHeader);
    chain.proceed(request, entityProducer, scope, asyncExecCallback);
  }
}
