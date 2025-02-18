/*
<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/spring/client/properties/CamundaClientLegacyPropertiesMappingsLoaderTest.java
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
=======
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.spring.client.properties;
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/test/java/io/camunda/zeebe/spring/client/properties/CamundaClientLegacyPropertiesMappingsLoaderTest.java

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

public class CamundaClientLegacyPropertiesMappingsLoaderTest {
  @Test
  void shouldLoadLegacyProperties() {
    final List<CamundaClientLegacyPropertiesMapping> load =
        CamundaClientLegacyPropertiesMappingsLoader.load();
    assertThat(load).isNotEmpty();
  }
}
