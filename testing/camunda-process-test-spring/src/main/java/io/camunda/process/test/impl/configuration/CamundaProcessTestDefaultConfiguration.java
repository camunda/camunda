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
package io.camunda.process.test.impl.configuration;

import static io.camunda.spring.client.configuration.CamundaAutoConfiguration.DEFAULT_OBJECT_MAPPER;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Fallback values if certain beans are missing */
public class CamundaProcessTestDefaultConfiguration {

  @Bean(name = "camundaJsonMapper")
  @ConditionalOnMissingBean
  public JsonMapper camundaJsonMapper(final ObjectMapper objectMapper) {
    return new CamundaObjectMapper(objectMapper);
  }

  @Bean(name = "zeebeJsonMapper")
  @ConditionalOnMissingBean
  public io.camunda.zeebe.client.api.JsonMapper zeebeJsonMapper(final ObjectMapper objectMapper) {
    return new ZeebeObjectMapper(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper() {
    return DEFAULT_OBJECT_MAPPER;
  }
}
