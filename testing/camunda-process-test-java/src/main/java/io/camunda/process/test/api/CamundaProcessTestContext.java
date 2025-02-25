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
package io.camunda.process.test.api;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/** The injected context for a process test. */
public interface CamundaProcessTestContext {

  /**
   * Creates a new preconfigured Camunda client that is managed by the runtime.
   *
   * @return a new Camunda client
   */
  CamundaClient createClient();

  /**
   * Creates a new preconfigured Camunda client that is managed by the runtime. The given modifier
   * can customize the client.
   *
   * @param modifier to customize the Camunda client
   * @return a new Camunda client
   */
  CamundaClient createClient(final Consumer<CamundaClientBuilder> modifier);

  /**
   * Creates a new preconfigured Zeebe client that is managed by the runtime.
   *
   * @return a new Zeebe client
   * @deprecated used to keep compatibility with the Zeebe client injection
   */
  @Deprecated
  ZeebeClient createZeebeClient();

  /**
   * Creates a new preconfigured Zeebe client that is managed by the runtime. The given modifier can
   * customize the client.
   *
   * @param modifier to customize the Zeebe client
   * @return a new Zeebe client
   * @deprecated used to keep compatibility with the Zeebe client injection
   */
  @Deprecated
  ZeebeClient createZeebeClient(Consumer<ZeebeClientBuilder> modifier);

  /**
   * @return the URI of Camunda's gRPC API address
   */
  URI getCamundaGrpcAddress();

  /**
   * @return the URI of Camunda's REST API address
   */
  URI getCamundaRestAddress();

  /**
   * @return the URI of the connectors REST API address
   */
  URI getConnectorsAddress();

  /**
   * The current time may differ from the system time if the time was modified using {@link
   * #increaseTime(Duration)}.
   *
   * @return the current time for the process tests
   */
  Instant getCurrentTime();

  /**
   * Modifies the current time for the process tests. It can be used to jump to the future to avoid
   * waiting until a due date is reached, for example, of a BPMN timer event.
   *
   * @param timeToAdd the duration to add to the current time
   */
  void increaseTime(final Duration timeToAdd);
}
