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

import io.camunda.client.spring.properties.MultiCamundaClientPropertiesResolver;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when a primary (default) client is resolvable from the multi-client configuration: a
 * single client, or several with a designated primary.
 *
 * <p>Resolution goes through the configuration properties (not the client bean definitions), so it
 * is evaluated correctly at {@code ConfigurationClassPostProcessor} time — before {@link
 * MultiCamundaClientBeanDefinitionRegistryPostProcessor} contributes the per-client {@code
 * CamundaClient} bean definitions. Beans that need the primary client (e.g. the actuator health
 * check, or the exported {@code CamundaClientConfiguration}) gate on this so they register whenever
 * a primary exists and inject the {@code @Primary} client at runtime.
 */
public final class OnResolvablePrimaryClientCondition extends SpringBootCondition {

  @Override
  public ConditionOutcome getMatchOutcome(
      final ConditionContext context, final AnnotatedTypeMetadata metadata) {
    final boolean hasPrimary;
    try {
      hasPrimary =
          MultiCamundaClientPropertiesResolver.resolve(context.getEnvironment())
              .getPrimaryClientName()
              .isPresent();
    } catch (final RuntimeException e) {
      // invalid configuration: do not register the bean and let the real validation error
      // surface from the MultiCamundaClientProperties bean creation instead of from here
      return ConditionOutcome.noMatch("multi-client configuration could not be resolved");
    }
    return new ConditionOutcome(
        hasPrimary,
        hasPrimary
            ? "a primary client is resolvable"
            : "no primary client is resolvable (multiple clients without a designated primary)");
  }
}
