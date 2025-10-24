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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.impl.CamundaObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootTest(classes = {CustomJsonMapperIT.class, CustomJsonMapperIT.JsonConfiguration.class})
@CamundaSpringProcessTest
public class CustomJsonMapperIT {

  @Autowired private CamundaProcessTestContext processTestContext;
  @Autowired private CamundaClient client;

  @Test
  void customJsonMapperShouldReadVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put(
        "bpt2", new BlueprintTest(1L, LocalDateTime.now(), LocalDateTime.now(), "testBusinessKey"));

    processTestContext.mockJobWorker("bp_worker").thenComplete(variables);

    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("customJsonMapper/blueprint.bpmn")
        .send()
        .join();

    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("Process_Blueprint")
            .latestVersion()
            .send()
            .join();

    // then
    CamundaAssert.assertThat(processInstance)
        .hasVariableSatisfies(
            "bpt2",
            BlueprintTest.class,
            bpt -> {
              Assertions.assertThat(bpt.getBusinessKey()).isEqualTo("testBusinessKey");
              Assertions.assertThat(bpt.getId()).isEqualTo(1L);
            });
  }

  @Configuration
  public static class JsonConfiguration {
    @Bean
    public JsonMapper jsonMapper() {
      final ObjectMapper objectMapper =
          new ObjectMapper()
              .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true)
              .registerModule(new JavaTimeModule());
      return new CamundaObjectMapper(objectMapper);
    }
  }

  private static class BlueprintTest {
    private Long id;

    private LocalDateTime createdDate;

    private LocalDateTime modifiedDate;

    private String businessKey;

    public BlueprintTest() {}

    public BlueprintTest(
        final Long id,
        final LocalDateTime createdDate,
        final LocalDateTime modifiedDate,
        final String businessKey) {
      this.id = id;
      this.createdDate = createdDate;
      this.modifiedDate = modifiedDate;
      this.businessKey = businessKey;
    }

    public Long getId() {
      return id;
    }

    public void setId(final Long id) {
      this.id = id;
    }

    public LocalDateTime getCreatedDate() {
      return createdDate;
    }

    public void setCreatedDate(final LocalDateTime createdDate) {
      this.createdDate = createdDate;
    }

    public LocalDateTime getModifiedDate() {
      return modifiedDate;
    }

    public void setModifiedDate(final LocalDateTime modifiedDate) {
      this.modifiedDate = modifiedDate;
    }

    public String getBusinessKey() {
      return businessKey;
    }

    public void setBusinessKey(final String businessKey) {
      this.businessKey = businessKey;
    }
  }
}
