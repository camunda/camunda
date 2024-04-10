/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.qa.util;

import static io.camunda.tasklist.util.ThreadUtil.sleepFor;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.DeployProcessCommandStep1;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ZeebeTestUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeTestUtil.class);

  private static Random random = new Random();

  public static String deployProcess(
      ZeebeClient client, BpmnModelInstance processModel, String resourceName) {
    final DeployProcessCommandStep1 deployProcessCommandStep1 =
        client.newDeployCommand().addProcessModel(processModel, resourceName);
    final DeploymentEvent deploymentEvent =
        ((DeployProcessCommandStep1.DeployProcessCommandBuilderStep2) deployProcessCommandStep1)
            .send()
            .join();
    LOGGER.debug("Deployment of resource [{}] was performed", resourceName);
    return String.valueOf(deploymentEvent.getProcesses().get(0).getProcessDefinitionKey());
  }

  public static ZeebeFuture<ProcessInstanceEvent> startProcessInstanceAsync(
      ZeebeClient client, String bpmnProcessId, String payload) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            client.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    if (payload != null) {
      createProcessInstanceCommandStep3.variables(payload);
    }
    return createProcessInstanceCommandStep3.send();
  }

  public static long startProcessInstance(
      ZeebeClient client, String bpmnProcessId, String payload) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            client.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    if (payload != null) {
      createProcessInstanceCommandStep3.variables(payload);
    }
    final ProcessInstanceEvent processInstanceEvent =
        createProcessInstanceCommandStep3.send().join();
    LOGGER.debug("Process instance created for process [{}]", bpmnProcessId);
    return processInstanceEvent.getProcessInstanceKey();
  }

  public static void completeTask(
      ZeebeClient client, String jobType, String workerName, String payload, int count) {
    final int[] countCompleted = {0};
    final JobWorker jobWorker =
        client
            .newWorker()
            .jobType(jobType)
            .handler(
                (jobClient, job) -> {
                  if (countCompleted[0] < count) {
                    CompleteJobCommandStep1 completeJobCommandStep1 =
                        jobClient.newCompleteCommand(job.getKey());
                    if (payload != null) {
                      completeJobCommandStep1 = completeJobCommandStep1.variables(payload);
                    }
                    completeJobCommandStep1.send().join();
                    LOGGER.debug("Task completed jobKey [{}]", job.getKey());
                    countCompleted[0]++;
                    if (countCompleted[0] % 1000 == 0) {
                      LOGGER.info("{} jobs completed ", countCompleted[0]);
                    }
                  }
                })
            .name(workerName)
            .timeout(Duration.ofSeconds(2))
            .open();
    // wait till all requested tasks are completed
    while (countCompleted[0] < count) {
      sleepFor(1000);
    }
    jobWorker.close();
  }

  public static void failTask(
      ZeebeClient client, String jobType, String workerName, int incidentCount) {
    failTask(client, jobType, workerName, null, incidentCount);
  }

  public static void failTask(
      ZeebeClient client,
      String jobType,
      String workerName,
      String errorMessage,
      int incidentCount) {
    final int[] countFailed = {0};
    final JobWorker jobWorker =
        client
            .newWorker()
            .jobType(jobType)
            .handler(
                (jobClient, activatedJob) -> {
                  final String error =
                      errorMessage == null ? "Error " + random.nextInt(50) : errorMessage;
                  if (countFailed[0] < incidentCount) {
                    client
                        .newFailCommand(activatedJob.getKey())
                        .retries(0)
                        .errorMessage(error)
                        .send()
                        .join();
                    countFailed[0]++;
                    if (countFailed[0] % 1000 == 0) {
                      LOGGER.info("{} jobs failed ", countFailed[0]);
                    }
                  }
                })
            .name(workerName)
            .timeout(Duration.ofSeconds(2))
            .open();
    // wait till all incidents are created
    while (countFailed[0] < incidentCount) {
      sleepFor(200);
    }
    jobWorker.close();
  }
}
