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
package io.camunda.process.test.impl.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntime;
import io.camunda.process.test.impl.spec.dsl.ProcessSpec;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProcessSpecRunnerIT {

  private final ObjectMapper objectMapper =
      new ObjectMapper().enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);

  private CamundaContainerRuntime camundaContainerRuntime;
  private CamundaProcessTestContext camundaProcessTestContext;
  private final List<AutoCloseable> clients = new ArrayList<>();

  @BeforeEach
  void createRuntime() {
    camundaContainerRuntime = CamundaContainerRuntime.newDefaultRuntime();
    camundaContainerRuntime.start();

    camundaProcessTestContext =
        new CamundaProcessTestContextImpl(
            camundaContainerRuntime.getCamundaContainer(),
            camundaContainerRuntime.getConnectorsContainer(),
            clients::add);
  }

  @AfterEach
  void closeRuntime() throws Exception {
    for (final AutoCloseable client : clients) {
      client.close();
    }

    camundaContainerRuntime.close();
  }

  @Test
  void shouldRunProcessSpec() throws IOException {
    //
    final InputStream serializedProcessSpec =
        getClass().getResourceAsStream("/specs/test-spec-1.spec");
    assertThat(serializedProcessSpec).describedAs("Test resource not found").isNotNull();

    final ProcessSpec processSpec = parse(serializedProcessSpec);

    // when
    final ProcessSpecRunner processSpecRunner = new ProcessSpecRunner(camundaProcessTestContext);
    final ProcessSpecResult result = processSpecRunner.runSpec(processSpec);

    // then
    assertThat(result.getTestResults())
        .hasSize(2)
        .extracting(SpecTestCaseResult::getName, SpecTestCaseResult::isSuccess)
        .containsSequence(tuple("case-1", true), tuple("case-2", true));
  }

  private ProcessSpec parse(final InputStream testSpecification) throws IOException {
    return objectMapper.readValue(testSpecification, ProcessSpec.class);
  }
}
