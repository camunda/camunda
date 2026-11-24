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
package io.camunda.client.spring.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaJackson3ObjectMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * Jackson 3 cases of {@link JsonMapperAutoConfigurationTest}, which must keep the mapper they get
 * since 8.9. Separate because the Spring Boot 3 starter, which compiles the same test sources, has
 * no Jackson 3 and excludes this class.
 */
@SpringBootTest(classes = JsonMapperAutoConfigurationJackson3Test.AutoConfiguredApp.class)
class JsonMapperAutoConfigurationJackson3Test {

  private static final long EPOCH_MILLIS = Instant.parse("2026-11-19T00:00:00Z").toEpochMilli();

  @Nested
  @TestPropertySource(
      properties =
          "spring.autoconfigure.exclude="
              + "org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration")
  class WithOnlySpringJackson3ObjectMapper {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldUseDefaultMapper() {
      // given
      final Map<String, Object> variables = Map.of("date", new Date(EPOCH_MILLIS));

      // when
      final String json = jsonMapper.toJson(variables);

      // then
      assertThat(jsonMapper).isInstanceOf(CamundaObjectMapper.class);
      assertThat(json).isEqualTo("{\"date\":" + EPOCH_MILLIS + "}");
    }
  }

  @Nested
  @TestPropertySource(properties = "test.own-jackson3-object-mapper=true")
  class WithOwnJackson3ObjectMapperAndSpringJackson2ObjectMapper {

    @Autowired JsonMapper jsonMapper;

    @Test
    void shouldUseJackson3ObjectMapper() {
      // when / then
      assertThat(jsonMapper).isInstanceOf(CamundaJackson3ObjectMapper.class);
    }
  }

  @Configuration
  @EnableAutoConfiguration
  static class AutoConfiguredApp {

    @Bean
    @ConditionalOnProperty("test.own-jackson3-object-mapper")
    ObjectMapper ownJackson3ObjectMapper() {
      return new ObjectMapper();
    }
  }
}
