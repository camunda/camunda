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
package io.camunda.process.test.api;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.impl.CamundaJackson3ObjectMapper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies the variable assertions end-to-end when the Camunda client serializes with Jackson 3,
 * which is the client's default on Spring Boot 4.
 *
 * <p>The application declares a Jackson 3 {@code ObjectMapper} bean so that {@code
 * Jackson3JsonMapperConfiguration} contributes the {@link CamundaJackson3ObjectMapper}. Depending
 * on Spring Boot's own Jackson auto-configuration instead would decide the mapper for every test in
 * this module, and would replace the Jackson 2 coverage rather than add to it.
 */
@SpringBootTest(classes = {Jackson3JsonMapperIT.TestApplication.class})
@CamundaSpringProcessTest
public class Jackson3JsonMapperIT {

  @Autowired private CamundaClient client;
  @Autowired private JsonMapper jsonMapper;

  @Test
  void shouldConfigureJackson3JsonMapper() {
    // then
    assertThat(jsonMapper).isInstanceOf(CamundaJackson3ObjectMapper.class);
  }

  @Test
  void shouldHaveVariable() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("jackson3-process").startEvent().endEvent().done();

    client
        .newDeployResourceCommand()
        .addProcessModel(process, "jackson3-process.bpmn")
        .send()
        .join();

    // when
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("jackson3-process")
            .latestVersion()
            .variables(Collections.singletonMap("isRefund", true))
            .send()
            .join();

    // then
    assertThatProcessInstance(processInstance).hasVariable("isRefund", true);
  }

  @Configuration
  static class TestApplication {

    @Bean
    public tools.jackson.databind.ObjectMapper jackson3ObjectMapper() {
      return new tools.jackson.databind.ObjectMapper();
    }
  }
}
