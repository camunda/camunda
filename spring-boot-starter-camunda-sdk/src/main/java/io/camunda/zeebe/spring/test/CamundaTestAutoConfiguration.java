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
package io.camunda.zeebe.spring.test;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.spring.client.configuration.CamundaAutoConfiguration;
import io.camunda.zeebe.spring.client.testsupport.SpringZeebeTestContext;
import io.camunda.zeebe.spring.test.proxy.TestProxyConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
@ImportAutoConfiguration({
  TestProxyConfiguration.class,
  ZeebeTestDefaultConfiguration.class,
  CamundaAutoConfiguration.class
})
@AutoConfigureBefore(CamundaAutoConfiguration.class)
public class CamundaTestAutoConfiguration {

  public static final ObjectMapper DEFAULT_OBJECT_MAPPER =
      new ObjectMapper()
          .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
          .configure(ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);

  @Bean
  public SpringZeebeTestContext enableTestContext() {
    // add marker bean to Spring context that we are running in a test case
    return new SpringZeebeTestContext();
  }
}
