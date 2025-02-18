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
<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/main/java/io/camunda/spring/client/properties/CamundaClientLegacyPropertiesMapping.java
package io.camunda.spring.client.properties;
=======
package io.camunda.zeebe.spring.client.properties;
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/main/java/io/camunda/zeebe/spring/client/properties/CamundaClientLegacyPropertiesMapping.java

import java.util.List;

public class CamundaClientLegacyPropertiesMapping {
  private String propertyName;
  private List<String> legacyPropertyNames;

  public String getPropertyName() {
    return propertyName;
  }

  public void setPropertyName(final String propertyName) {
    this.propertyName = propertyName;
  }

  public List<String> getLegacyPropertyNames() {
    return legacyPropertyNames;
  }

  public void setLegacyPropertyNames(final List<String> legacyPropertyNames) {
    this.legacyPropertyNames = legacyPropertyNames;
  }
}
