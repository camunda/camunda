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
import io.camunda.client.spring.properties.CamundaClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Fallback mapper. The ObjectMapper of Spring's Jackson 2 auto-configuration, or of a library
 * ordered after CamundaAutoConfiguration, is registered after the bean condition of {@link
 * JsonMapperConfiguration} is evaluated, so that configuration backs off. This one looks up the
 * Jackson 2 ObjectMapper again when the bean is created.
 */
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

  @Autowired private ObjectProvider<ObjectMapper> objectMapper;
  @Autowired private ObjectProvider<CamundaClientProperties> camundaClientProperties;

  @Bean(name = "camundaJsonMapper")
  @ConditionalOnMissingBean
  @SuppressWarnings("removal")
  public JsonMapper jsonMapper() {
    // Only reached when no own JsonMapper exists and the other mapper configurations backed off.
    // Unlike the bean condition of JsonMapperConfiguration, this runs after all beans are
    // registered, so it finds Spring's Jackson 2 ObjectMapper.
    final CamundaClientProperties properties = camundaClientProperties.getIfAvailable();
    final boolean useBareObjectMapper = properties != null && properties.isUseBareObjectMapper();
    if (useBareObjectMapper) {
      LOG.warn(
          "The property 'camunda.client.use-bare-object-mapper' is deprecated and will be removed"
              + " in 8.11, which always uses the application's ObjectMapper");
    }
    final ObjectMapper springObjectMapper = useBareObjectMapper ? null : objectMapper.getIfUnique();
    if (springObjectMapper != null) {
      LOG.debug("Using jackson 2 to configure Camunda client json mapper");
      return new CamundaObjectMapper(springObjectMapper.copy());
    }
    LOG.debug("Using the default jackson 2 Camunda client json mapper");
    return new CamundaObjectMapper();
  }
}
