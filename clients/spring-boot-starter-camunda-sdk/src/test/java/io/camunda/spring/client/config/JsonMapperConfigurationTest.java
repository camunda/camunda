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

package io.camunda.spring.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.JsonMapper;
import io.camunda.spring.client.config.JsonMapperConfigurationTest.JacksonConfiguration;
import io.camunda.spring.client.configuration.JsonMapperConfiguration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootTest(classes = {JacksonConfiguration.class, JsonMapperConfiguration.class})
public class JsonMapperConfigurationTest {
  @Autowired private ObjectMapper defaultObjectMapper;
  @Autowired private JsonMapper camundaJsonMapper;

  @Test
  public void shouldSerializeNullValuesInJson() throws Exception {
    // given
    final Map<String, Object> map = new HashMap<>();
    map.put("key", null);
    map.put("key2", "value2");
    // when

    final String json = camundaJsonMapper.toJson(map);
    final JsonNode zeebeJsonNode = new ObjectMapper().readTree(json);

    // then
    assertThat(zeebeJsonNode.get("key").isNull()).isTrue();
    assertThat(zeebeJsonNode.get("key2").asText()).isEqualTo("value2");
  }

  @Test
  public void shouldNotChangeDefaultObjectMapper() {
    // verify that creating camunda object mapper does not change config of default object mapper
    assertThat(defaultObjectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
        .isTrue();
  }

  @Configuration
  protected static class JacksonConfiguration {
    @Bean
    public ObjectMapper objectMapper() {
      // default object mapper config
      return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }
  }
}
