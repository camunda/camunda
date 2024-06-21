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
package io.camunda.zeebe.client;

import io.camunda.zeebe.client.impl.CamundaClientBuilderImpl;
import io.camunda.zeebe.client.impl.CamundaClientCloudBuilderImpl;
import io.camunda.zeebe.client.impl.CamundaClientImpl;

/** The client to communicate with Camunda platform */
public interface CamundaClient extends ZeebeClient {

  /**
   * @return a new Camunda client with default configuration values. In order to customize
   *     configuration, use the methods {@link #newClientBuilder()} or {@link
   *     #newClient(CamundaClientConfiguration)}. See {@link CamundaClientBuilder} for the
   *     configuration options and default values.
   */
  static CamundaClient newClient() {
    return newClientBuilder().build();
  }

  /**
   * @return a new {@link CamundaClient} using the provided configuration.
   */
  static CamundaClient newClient(final CamundaClientConfiguration configuration) {
    return new CamundaClientImpl(configuration);
  }

  /**
   * @return a builder to configure and create a new {@link CamundaClient}.
   */
  static CamundaClientBuilder newClientBuilder() {
    return new CamundaClientBuilderImpl();
  }

  /**
   * @return a builder with convenient methods to connect to the Camunda Cloud cluster.
   */
  static CamundaClientCloudBuilderStep1 newCloudClientBuilder() {
    return new CamundaClientCloudBuilderImpl();
  }

  /**
   * @return the client's configuration
   */
  @Override
  CamundaClientConfiguration getConfiguration();
}
