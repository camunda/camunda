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
package io.camunda.client.jobhandling.parameter;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.function.Function;

public class KeyParameterResolver implements ParameterResolver {
  private final KeyTargetType keyTargetType;
  private final Function<ActivatedJob, Long> keyResolver;

  public KeyParameterResolver(
      final KeyTargetType keyTargetType, final Function<ActivatedJob, Long> keyResolver) {
    this.keyTargetType = keyTargetType;
    this.keyResolver = keyResolver;
  }

  public KeyTargetType getKeyTargetType() {
    return keyTargetType;
  }

  @Override
  public final Object resolve(final JobClient jobClient, final ActivatedJob job) {
    final Long key = keyResolver.apply(job);
    return switch (keyTargetType) {
      case LONG -> key;
      case STRING -> String.valueOf(key);
    };
  }
}
