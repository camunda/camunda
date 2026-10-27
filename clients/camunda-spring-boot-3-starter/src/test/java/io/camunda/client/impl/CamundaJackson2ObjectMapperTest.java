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
package io.camunda.client.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Spring Boot 3 compatible equivalent of CamundaObjectMapperTest. Tests the Jackson 2
 * (com.fasterxml.jackson) based CamundaObjectMapper instead of CamundaJackson3ObjectMapper.
 */
public class CamundaJackson2ObjectMapperTest {

  @Test
  public void shouldReturnEmptyObject() {
    // Given
    final CamundaObjectMapper camundaObjectMapper = new CamundaObjectMapper();
    final TestObject testObject = new TestObject();
    testObject.setObject(new Object());
    // When
    final String actual = camundaObjectMapper.toJson(testObject);
    // Then
    assertThat(actual).isEqualTo("{\"object\":{}}");
  }

  public static final class TestObject {
    private Object object;

    public Object getObject() {
      return object;
    }

    public void setObject(final Object object) {
      this.object = object;
    }
  }
}
