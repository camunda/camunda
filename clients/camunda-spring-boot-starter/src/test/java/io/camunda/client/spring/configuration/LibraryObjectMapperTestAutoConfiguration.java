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
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Stands in for library auto-configurations that provide ObjectMappers and are ordered after
 * CamundaAutoConfiguration, so the mappers are not registered yet when Camunda's JSON mapper
 * configuration is processed. Each bean is only active when its {@code test.*} property is set.
 */
@AutoConfiguration
@AutoConfigureAfter(CamundaAutoConfiguration.class)
public class LibraryObjectMapperTestAutoConfiguration {

  @Bean
  @ConditionalOnProperty("test.library-object-mapper")
  public ObjectMapper libraryObjectMapper() {
    return new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
  }

  @Bean
  @ConditionalOnProperty("test.second-library-object-mapper")
  public ObjectMapper secondLibraryObjectMapper() {
    return new ObjectMapper();
  }

  @Bean
  @Primary
  @ConditionalOnProperty("test.primary-library-object-mapper")
  public ObjectMapper primaryLibraryObjectMapper() {
    return new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
  }

  @Bean
  @Primary
  @ConditionalOnProperty("test.second-primary-library-object-mapper")
  public ObjectMapper secondPrimaryLibraryObjectMapper() {
    return new ObjectMapper();
  }

  @Bean
  @ConditionalOnProperty("test.library-non-json-mapper")
  public ObjectMapper libraryNonJsonMapper() {
    return new ObjectMapper(new NonJsonFactory())
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
  }

  @Bean
  @ConditionalOnProperty("test.library-non-copyable-object-mapper")
  public ObjectMapper libraryNonCopyableObjectMapper() {
    // copy() throws for subclasses that do not override it
    return new ObjectMapper() {};
  }

  /** Stands in for factories of other formats, like YAMLFactory, which extend JsonFactory. */
  static class NonJsonFactory extends JsonFactory {

    @Override
    public JsonFactory copy() {
      return new NonJsonFactory();
    }

    @Override
    public String getFormatName() {
      return "non-JSON";
    }
  }
}
