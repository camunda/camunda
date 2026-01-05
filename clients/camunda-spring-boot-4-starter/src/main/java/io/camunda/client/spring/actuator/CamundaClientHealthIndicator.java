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
package io.camunda.client.spring.actuator;

import io.camunda.client.health.HealthCheck;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;

/**
 * Spring Boot 4 compatible health indicator for Camunda Client. Uses the new package location for
 * health classes in Spring Boot 4.x.
 */
public class CamundaClientHealthIndicator extends AbstractHealthIndicator {

  private final HealthCheck healthCheck;

  public CamundaClientHealthIndicator(final HealthCheck healthCheck) {
    this.healthCheck = healthCheck;
  }

  @Override
  protected void doHealthCheck(final Health.Builder builder) {
    switch (healthCheck.health()) {
      case UP -> builder.up();
      case DOWN -> builder.down();
      default -> throw new IllegalStateException("Unexpected value: " + healthCheck.health());
    }
  }
}
