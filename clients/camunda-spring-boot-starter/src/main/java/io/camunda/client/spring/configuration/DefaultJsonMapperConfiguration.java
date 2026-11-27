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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
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

  @Bean(name = "camundaJsonMapper")
  @ConditionalOnMissingBean
  public JsonMapper jsonMapper() {
    // Only reached when no own JsonMapper exists and the other mapper configurations backed off.
    // Unlike the bean condition of JsonMapperConfiguration, this runs after all beans are
    // registered, so it finds Spring's Jackson 2 ObjectMapper.
    try {
      final ObjectMapper springObjectMapper = objectMapper.getIfUnique();
      if (springObjectMapper != null && isJson(springObjectMapper)) {
        LOG.debug("Using jackson 2 to configure Camunda client json mapper");
        return new CamundaObjectMapper(springObjectMapper.copy());
      }
    } catch (final BeansException | IllegalStateException e) {
      LOG.warn(
          "Cannot use the Jackson 2 ObjectMapper for the Camunda client, using the default json"
              + " mapper. Define a JsonMapper bean to configure it.",
          e);
    }
    LOG.debug("Using the default jackson 2 Camunda client json mapper");
    return new CamundaObjectMapper();
  }

  private static boolean isJson(final ObjectMapper objectMapper) {
    final String formatName = objectMapper.getFactory().getFormatName();
    if (JsonFactory.FORMAT_NAME_JSON.equals(formatName)) {
      return true;
    }
    LOG.debug(
        "Ignoring the application's {} ObjectMapper for the Camunda client json mapper",
        formatName);
    return false;
  }
}
