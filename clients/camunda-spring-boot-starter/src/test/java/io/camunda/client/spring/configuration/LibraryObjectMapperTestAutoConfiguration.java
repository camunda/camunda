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
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Stands in for a library auto-configuration that provides the application's ObjectMapper and is
 * ordered after CamundaAutoConfiguration, so the mapper is not registered yet when Camunda's JSON
 * mapper configuration is processed. Only active when {@code test.library-object-mapper} is set.
 */
@AutoConfiguration
@AutoConfigureAfter(CamundaAutoConfiguration.class)
@ConditionalOnProperty("test.library-object-mapper")
public class LibraryObjectMapperTestAutoConfiguration {

  @Bean
  public ObjectMapper libraryObjectMapper() {
    return new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
  }
}
