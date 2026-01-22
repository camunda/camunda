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
package io.camunda.client.spring.configuration.condition;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition that matches when single-client (traditional) configuration is being used. This is the
 * inverse of {@link OnMultiClientConfigurationCondition}.
 *
 * <p>Matches when NO clients are configured under {@code camunda.clients.*}.
 */
public class OnSingleClientConfigurationCondition extends SpringBootCondition {

  private static final String MULTI_CLIENT_PREFIX = "camunda.clients";

  @Override
  public ConditionOutcome getMatchOutcome(
      final ConditionContext context, final AnnotatedTypeMetadata metadata) {
    final Binder binder = Binder.get(context.getEnvironment());

    // Try to bind camunda.clients as a Map - this works regardless of the value types
    final BindResult<Map<String, Object>> bindResult =
        binder.bind(MULTI_CLIENT_PREFIX, Bindable.mapOf(String.class, Object.class));

    if (bindResult.isBound() && !bindResult.get().isEmpty()) {
      return ConditionOutcome.noMatch(
          "Multi-client configuration found, skipping single-client mode");
    }

    return ConditionOutcome.match("No multi-client configuration found, using single-client mode");
  }
}
