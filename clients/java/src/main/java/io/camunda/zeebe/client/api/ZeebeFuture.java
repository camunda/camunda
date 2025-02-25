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
package io.camunda.zeebe.client.api;

import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link io.camunda.client.api.CamundaFuture}
 */
@Deprecated
public interface ZeebeFuture<T> extends Future<T>, CompletionStage<T> {

  /**
   * Like {@link #get()} but throws runtime exceptions.
   *
   * @throws ClientStatusException on gRPC errors
   * @throws ClientException on unexpected errors
   */
  T join();

  /**
   * Like {@link #get(long, TimeUnit)} but throws runtime exceptions.
   *
   * @throws ClientStatusException on gRPC errors
   * @throws ClientException on unexpected errors
   */
  T join(long timeout, TimeUnit unit);
}
