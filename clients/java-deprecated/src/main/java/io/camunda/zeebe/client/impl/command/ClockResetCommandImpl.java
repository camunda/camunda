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
package io.camunda.zeebe.client.impl.command;

import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.ClockResetCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ResetClockResponse;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

/**
 * @deprecated since 8.8 for removal in 8.10, replaced by {@link
 *     io.camunda.client.impl.command.ClockResetCommandImpl}. Please see the <a
 *     href="https://docs.camunda.io/docs/8.8/apis-tools/migration-manuals/migrate-to-camunda-java-client/">Camunda
 *     Java Client migration guide</a>
 */
@Deprecated
public class ClockResetCommandImpl implements ClockResetCommandStep1 {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public ClockResetCommandImpl(final HttpClient httpClient) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalCommandStep<ResetClockResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<ResetClockResponse> send() {
    final HttpZeebeFuture<ResetClockResponse> result = new HttpZeebeFuture<>();
    httpClient.post("/clock/reset", "", httpRequestConfig.build(), result);
    return result;
  }
}
