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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.testsupport.CamundaSpringProcessTestContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Regression coverage for the process-test-support integration (#57344 follow-up): when a {@link
 * CamundaSpringProcessTestContext} is present, the test framework supplies its own primary {@link
 * CamundaClient}, so the unified auto-configuration must NOT register per-client beans — otherwise
 * two {@code @Primary} clients would break the context (as the process-test ITs showed).
 */
@SpringBootTest(
    classes = {
      ProcessTestSupportClientRegistrationTest.TestSupportConfig.class,
      CamundaAutoConfiguration.class
    },
    properties = {"camunda.client.rest-address=https://localhost:8080"})
public class ProcessTestSupportClientRegistrationTest {

  @Autowired private ApplicationContext applicationContext;
  @Autowired private CamundaClient camundaClient;

  @Test
  void shouldNotRegisterClientBeansWhenTestSupportIsPresent() {
    // only the test framework's client exists; no per-client default bean was registered
    assertThat(applicationContext.getBeanNamesForType(CamundaClient.class))
        .containsExactly("testCamundaClient");
    assertThat(applicationContext.containsBean("defaultCamundaClient")).isFalse();
    assertThat(camundaClient).isSameAs(applicationContext.getBean("testCamundaClient"));
  }

  @TestConfiguration
  static class TestSupportConfig {
    @Bean
    CamundaSpringProcessTestContext enableTestContext() {
      return new CamundaSpringProcessTestContext();
    }

    @Bean
    @Primary
    CamundaClient testCamundaClient() {
      return mock(CamundaClient.class);
    }
  }
}
