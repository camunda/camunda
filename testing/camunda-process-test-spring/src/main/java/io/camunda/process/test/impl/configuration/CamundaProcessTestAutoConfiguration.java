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

import io.camunda.spring.client.configuration.CamundaAutoConfiguration;
import io.camunda.spring.client.testsupport.CamundaSpringProcessTestContext;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
@ImportAutoConfiguration({
  CamundaProcessTestProxyConfiguration.class,
  CamundaProcessTestDefaultConfiguration.class,
  CamundaAutoConfiguration.class,
  CamundaProcessTestRuntimeConfiguration.class,
  LegacyCamundaProcessTestRuntimeConfiguration.class,
})
@AutoConfigureBefore(CamundaAutoConfiguration.class)
public class CamundaProcessTestAutoConfiguration {

  @Bean
  public CamundaSpringProcessTestContext enableTestContext() {
    // add marker bean to Spring context that we are running in a test case
    return new CamundaSpringProcessTestContext();
  }
}
