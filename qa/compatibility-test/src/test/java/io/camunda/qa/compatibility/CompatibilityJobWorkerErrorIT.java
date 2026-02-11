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
package io.camunda.qa.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.worker.JobClient;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

@SpringBootTest(
    classes = CompatibilityJobWorkerErrorIT.TestApplication.class,
    properties = {"spring.main.web-application-type=none"})
@CamundaSpringProcessTest
class CompatibilityJobWorkerErrorIT {

  private static final String PROCESS_ID = "compatibilityErrorProcess";
  private static final String JOB_TYPE = "compatibility-error-worker";
  private static final String BPMN_RESOURCE = "bpmn/compatibility-bpmn-error.bpmn";
  private static final String ERROR_CODE = "ERR_CODE";

  @Autowired private CamundaClient camundaClient;

  @Test
  void shouldPropagateBpmnErrorFromWorker() {
    // given
    final var deployment =
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath(BPMN_RESOURCE)
            .send()
            .join();

    // when
    final ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join();

    // then
    assertThat(deployment.getProcesses()).hasSize(1);
    CamundaAssert.assertThat(processInstance)
        .isCompleted()
        .hasCompletedElementsInOrder("start", "errorBoundary", "errorEnd");
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({ErrorJobWorker.class, CompatibilityTestSupportConfiguration.class})
  static class TestApplication {}

  @Component
  public static class ErrorJobWorker {

    @JobWorker(type = JOB_TYPE, autoComplete = false)
    public void handleJob(final JobClient jobClient, final ActivatedJob job) {
      jobClient
          .newThrowErrorCommand(job.getKey())
          .errorCode(ERROR_CODE)
          .variables(Map.of("errorHandled", true))
          .send()
          .join();
    }
  }
}
