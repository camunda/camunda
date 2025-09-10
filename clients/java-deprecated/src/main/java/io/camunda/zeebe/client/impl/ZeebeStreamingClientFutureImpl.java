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

package io.camunda.zeebe.client.impl;

import java.util.function.Consumer;

/**
 * @deprecated since 8.8 for removal in 8.10, replaced by {@link
 *     io.camunda.client.impl.ZeebeStreamingClientFutureImpl}. Please see the <a
 *     href="https://docs.camunda.io/docs/8.8/apis-tools/migration-manuals/migrate-to-camunda-java-client/">Camunda
 *     Java Client migration guide</a>
 */
@Deprecated
public class ZeebeStreamingClientFutureImpl<ClientResponse, BrokerResponse>
    extends ZeebeClientFutureImpl<ClientResponse, BrokerResponse> {

  private final ClientResponse response;
  private final Consumer<BrokerResponse> collector;

  public ZeebeStreamingClientFutureImpl(
      final ClientResponse response, final Consumer<BrokerResponse> collector) {
    this.response = response;
    this.collector = collector;
  }

  @Override
  public void onNext(final BrokerResponse brokerResponse) {
    try {
      collector.accept(brokerResponse);
    } catch (final Exception e) {
      completeExceptionally(e);
      rethrow(e);
    }
  }

  @Override
  public void onError(final Throwable throwable) {
    completeExceptionally(throwable);
  }

  @Override
  public void onCompleted() {
    complete(response);
  }

  @SuppressWarnings("unchecked")
  private <T extends Throwable> void rethrow(final Throwable exception) throws T {
    throw (T) exception;
  }
}
