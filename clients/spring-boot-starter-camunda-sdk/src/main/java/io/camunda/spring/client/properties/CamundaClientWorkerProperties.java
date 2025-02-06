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
package io.camunda.spring.client.properties;

import io.camunda.spring.client.annotation.value.JobWorkerValue;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class CamundaClientWorkerProperties {
  @NestedConfigurationProperty private JobWorkerValue defaults = new JobWorkerValue();
  @NestedConfigurationProperty private Map<String, JobWorkerValue> override = new HashMap<>();

  public JobWorkerValue getDefaults() {
    return defaults;
  }

  public void setDefaults(final JobWorkerValue defaults) {
    this.defaults = defaults;
  }

  public Map<String, JobWorkerValue> getOverride() {
    return override;
  }

  public void setOverride(final Map<String, JobWorkerValue> override) {
    this.override = override;
  }

  @Override
  public String toString() {
    return "CamundaClientWorkerProperties{"
        + "defaults="
        + defaults
        + ", override="
        + override
        + '}';
  }
}
