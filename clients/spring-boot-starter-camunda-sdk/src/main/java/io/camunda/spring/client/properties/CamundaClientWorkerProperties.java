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

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class CamundaClientWorkerProperties {
  @NestedConfigurationProperty
  private CamundaClientJobWorkerProperties defaults = new CamundaClientJobWorkerProperties(true);

  @NestedConfigurationProperty
  private Map<String, CamundaClientJobWorkerProperties> override = new HashMap<>();

  public CamundaClientJobWorkerProperties getDefaults() {
    return defaults;
  }

  public void setDefaults(final CamundaClientJobWorkerProperties defaults) {
    this.defaults = defaults;
  }

  public Map<String, CamundaClientJobWorkerProperties> getOverride() {
    return override;
  }

  public void setOverride(final Map<String, CamundaClientJobWorkerProperties> override) {
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
