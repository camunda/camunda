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
package io.camunda.client.impl.fetch;

import io.camunda.client.api.ConsistencyPolicy;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.fetch.FinalFetchRequestStep;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public abstract class AbstractFetchRequestImpl<T>
    implements FinalFetchRequestStep<T>, FinalCommandStep<T> {

  protected ConsistencyPolicy<T> consistencyPolicy;
  protected final RequestConfig.Builder httpRequestConfig;

  protected AbstractFetchRequestImpl(final RequestConfig.Builder httpRequestConfig) {
    this.httpRequestConfig = httpRequestConfig;
  }

  @Override
  public FinalCommandStep<T> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public FinalCommandStep<T> consistencyPolicy(
      final Consumer<ConsistencyPolicy<T>> consistencyPolicyConsumer) {
    consistencyPolicy = new FetchRequestConsistencyPolicy<>();
    consistencyPolicyConsumer.accept(consistencyPolicy);
    return this;
  }

  @Override
  public FinalCommandStep<T> consistencyPolicy(final ConsistencyPolicy<T> consistencyPolicy) {
    this.consistencyPolicy = consistencyPolicy;
    return this;
  }

  @Override
  public FinalCommandStep<T> withDefaultConsistencyPolicy() {
    consistencyPolicy = new FetchRequestConsistencyPolicy<>();
    return this;
  }
}
