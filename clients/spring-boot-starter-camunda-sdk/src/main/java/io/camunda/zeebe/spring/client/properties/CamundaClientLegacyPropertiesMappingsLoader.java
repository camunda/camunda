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
package io.camunda.zeebe.spring.client.properties;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class CamundaClientLegacyPropertiesMappingsLoader {
  public static List<CamundaClientLegacyPropertiesMapping> load() {
    final Properties properties = new Properties();
    try (final InputStream inputStream =
        CamundaClientLegacyPropertiesMappingsLoader.class
            .getClassLoader()
            .getResourceAsStream("zeebe-client-legacy-property-mappings.properties")) {
      properties.load(inputStream);
    } catch (final IOException e) {
      throw new RuntimeException("Error while loading legacy property mappings", e);
    }
    final List<CamundaClientLegacyPropertiesMapping> mappings = new ArrayList<>();
    properties.forEach(
        (key, value) -> {
          final String propertyName = (String) key;
          final String legacyPropertyNamesString = (String) value;
          final List<String> legacyPropertyNames =
              Arrays.stream(legacyPropertyNamesString.split(",")).map(String::trim).toList();
          final CamundaClientLegacyPropertiesMapping propertyMapping =
              new CamundaClientLegacyPropertiesMapping();
          propertyMapping.setPropertyName(propertyName);
          propertyMapping.setLegacyPropertyNames(legacyPropertyNames);
          mappings.add(propertyMapping);
        });
    return mappings;
  }
}
