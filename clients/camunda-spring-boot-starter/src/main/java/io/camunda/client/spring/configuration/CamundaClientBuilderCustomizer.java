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
package io.camunda.client.spring.configuration;

import io.camunda.client.CamundaClientBuilder;

/**
 * Customizes the builder of every {@link io.camunda.client.CamundaClient} the starter creates,
 * after the configuration properties were applied to it. Register an implementation as a bean to
 * apply it.
 *
 * <p>Customizers run in the order given by {@link org.springframework.core.annotation.Order @Order}
 * or {@link org.springframework.core.Ordered}. The starter's own customizer, which sets the
 * application's meter registry when actuator is present, runs first, so a customizer of the
 * application can replace the registry for all clients.
 */
@FunctionalInterface
public interface CamundaClientBuilderCustomizer {

  void customize(CamundaClientBuilder builder);
}
