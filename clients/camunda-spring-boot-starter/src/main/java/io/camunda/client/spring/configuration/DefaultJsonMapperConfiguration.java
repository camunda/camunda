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

import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(
    name = {
      "org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration",
      "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration",
      "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
      "io.camunda.client.spring.configuration.JsonMapperConfiguration",
      "io.camunda.client.spring.configuration.Jackson3JsonMapperConfiguration"
    })
public class DefaultJsonMapperConfiguration {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultJsonMapperConfiguration.class);

  @Bean(name = "camundaJsonMapper")
  @ConditionalOnMissingBean
  public JsonMapper jsonMapper() {
    LOG.debug("Using jackson 2 to configure Camunda client json mapper");
    return new CamundaObjectMapper();
  }
}
