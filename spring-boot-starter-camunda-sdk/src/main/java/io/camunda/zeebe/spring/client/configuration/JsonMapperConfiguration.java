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
<<<<<<< HEAD:spring-boot-starter-camunda-sdk/src/main/java/io/camunda/zeebe/spring/client/configuration/JsonMapperConfiguration.java
package io.camunda.zeebe.spring.client.configuration;
=======
package io.camunda.process.test.impl.configuration;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
>>>>>>> e7c26b7d (refactor: move default objectMapper to test setup):testing/camunda-process-test-spring/src/main/java/io/camunda/process/test/impl/configuration/CamundaProcessTestDefaultConfiguration.java

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.common.json.SdkObjectMapper;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonMapperConfiguration {
  private final ObjectMapper objectMapper;

  @Autowired
  public JsonMapperConfiguration(@Autowired(required = false) final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Bean(name = "zeebeJsonMapper")
  @ConditionalOnMissingBean
  public JsonMapper jsonMapper() {
    if (objectMapper == null) {
      return new ZeebeObjectMapper();
    }
    return new ZeebeObjectMapper(objectMapper.copy());
  }

  @Bean(name = "commonJsonMapper")
  @ConditionalOnMissingBean
<<<<<<< HEAD:spring-boot-starter-camunda-sdk/src/main/java/io/camunda/zeebe/spring/client/configuration/JsonMapperConfiguration.java
  public io.camunda.common.json.JsonMapper commonJsonMapper() {
    if (objectMapper == null) {
      return new SdkObjectMapper();
    }
    return new SdkObjectMapper(objectMapper.copy());
=======
  public ObjectMapper objectMapper() {
    return new ObjectMapper()
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
>>>>>>> e7c26b7d (refactor: move default objectMapper to test setup):testing/camunda-process-test-spring/src/main/java/io/camunda/process/test/impl/configuration/CamundaProcessTestDefaultConfiguration.java
  }
}
