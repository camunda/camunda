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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Opt-in use of the application's Jackson 2 ObjectMapper, enabled by {@code
 * camunda.client.use-spring-object-mapper}. The ObjectMapper is looked up when the bean is created
 * rather than through a bean condition, so it is found regardless of auto-configuration order.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "camunda.client.use-spring-object-mapper", havingValue = "true")
public class SpringObjectMapperJsonMapperConfiguration {
  private static final Logger LOG =
      LoggerFactory.getLogger(SpringObjectMapperJsonMapperConfiguration.class);

  @Bean(name = "camundaJsonMapper")
  @ConditionalOnMissingBean
  public JsonMapper jsonMapper(final ObjectProvider<ObjectMapper> objectMapper) {
    final ObjectMapper springObjectMapper = objectMapper.getIfAvailable();
    if (springObjectMapper == null) {
      LOG.debug("No Jackson 2 ObjectMapper bean found, using the default Camunda json mapper");
      return new CamundaObjectMapper();
    }
    LOG.debug("Using a copy of the application's Jackson 2 ObjectMapper as Camunda json mapper");
    return new CamundaObjectMapper(springObjectMapper.copy());
  }
}
