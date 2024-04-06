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
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.ThreadUtil.sleepFor;
import static io.camunda.zeebe.client.api.command.CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1;
import io.camunda.zeebe.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ZeebeTestUtil {

  public static final Logger ALL_EVENTS_LOGGER =
      LoggerFactory.getLogger("io.camunda.tasklist.ALL_EVENTS");
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeTestUtil.class);

  /**
   * Deploys the process synchronously.
   *
   * @param client client
   * @param classpathResources classpath resources
   * @return process id
   */
  public static String deployProcess(ZeebeClient client, String... classpathResources) {
    return deployProcess(DEFAULT_TENANT_IDENTIFIER, client, classpathResources);
  }

  /**
   * Deploys the process synchronously.
   *
   * @param tenantId id of the tenant
   * @param client client
   * @param classpathResources classpath resources
   * @return process id
   */
  public static String deployProcess(
      String tenantId, ZeebeClient client, String... classpathResources) {
    if (classpathResources.length == 0) {
      return null;
    }
    DeployResourceCommandStep1 deployProcessCommandStep1 = client.newDeployResourceCommand();
    for (String classpathResource : classpathResources) {
      deployProcessCommandStep1 =
          deployProcessCommandStep1.addResourceFromClasspath(classpathResource).tenantId(tenantId);
    }
    final DeploymentEvent deploymentEvent =
        ((DeployResourceCommandStep1.DeployResourceCommandStep2) deployProcessCommandStep1)
            .send()
            .join();
    LOGGER.debug("Deployment of resource [{}] was performed", (Object[]) classpathResources);
    return String.valueOf(
        deploymentEvent
            .getProcesses()
            .get(classpathResources.length - 1)
            .getProcessDefinitionKey());
  }

  /**
   * Deploys the process synchronously.
   *
   * @param client client
   * @param processModel processModel
   * @param resourceName resourceName
   * @return process id
   */
  public static String deployProcess(
      ZeebeClient client, BpmnModelInstance processModel, String resourceName) {
    return deployProcess(DEFAULT_TENANT_IDENTIFIER, client, processModel, resourceName);
  }

  public static String deployProcess(
      ZeebeClient client, BpmnModelInstance processModel, String resourceName, String tenantId) {
    return deployProcess(tenantId, client, processModel, resourceName);
  }

  /**
   * Deploys the process synchronously.
   *
   * @param tenantId id of the tenant
   * @param client client
   * @param processModel processModel
   * @param resourceName resourceName
   * @return process id
   */
  public static String deployProcess(
      String tenantId, ZeebeClient client, BpmnModelInstance processModel, String resourceName) {
    final DeployResourceCommandStep1.DeployResourceCommandStep2 deployProcessCommandStep1 =
        client
            .newDeployResourceCommand()
            .addProcessModel(processModel, resourceName)
            .tenantId(tenantId);
    final DeploymentEvent deploymentEvent = deployProcessCommandStep1.send().join();
    LOGGER.debug("Deployment of resource [{}] was performed", resourceName);
    return String.valueOf(deploymentEvent.getProcesses().get(0).getProcessDefinitionKey());
  }

  public static void deleteResource(ZeebeClient client, long resourceKey) {
    client.newDeleteResourceCommand(resourceKey).send().join();
    LOGGER.debug("Deletion of resource [{}] was performed", resourceKey);
  }

  /**
   * @param client client
   * @param bpmnProcessId bpmnProcessId
   * @param payload payload
   * @return process instance id
   */
  public static String startProcessInstance(
      ZeebeClient client, String bpmnProcessId, String payload) {
    return startProcessInstance(DEFAULT_TENANT_IDENTIFIER, client, bpmnProcessId, payload);
  }

  /**
   * @param tenantId id of the tenant
   * @param client client
   * @param bpmnProcessId bpmnProcessId
   * @param payload payload
   * @return process instance id
   */
  public static String startProcessInstance(
      String tenantId, ZeebeClient client, String bpmnProcessId, String payload) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            client
                .newCreateInstanceCommand()
                .bpmnProcessId(bpmnProcessId)
                .latestVersion()
                .tenantId(tenantId);
    if (payload != null) {
      createProcessInstanceCommandStep3.variables(payload);
    }
    ProcessInstanceEvent processInstanceEvent;
    try {
      processInstanceEvent = createProcessInstanceCommandStep3.send().join();
      LOGGER.debug("Process instance created for process [{}]", bpmnProcessId);
    } catch (ClientException ex) {
      // retry once
      sleepFor(300L);
      processInstanceEvent = createProcessInstanceCommandStep3.send().join();
      LOGGER.debug("Process instance created for process [{}]", bpmnProcessId);
    }
    return String.valueOf(processInstanceEvent.getProcessInstanceKey());
  }

  public static void cancelProcessInstance(ZeebeClient client, long processInstanceKey) {
    client.newCancelInstanceCommand(processInstanceKey).send().join();
  }

  public static void completeTask(
      ZeebeClient client, String jobType, String workerName, String payload) {
    completeTask(client, jobType, workerName, payload, 1);
  }

  public static void completeTask(
      ZeebeClient client, String jobType, String workerName, String payload, int count) {
    handleTasks(
        client,
        jobType,
        workerName,
        count,
        (jobClient, job) -> {
          final CompleteJobCommandStep1 command = jobClient.newCompleteCommand(job.getKey());
          if (payload != null) {
            command.variables(payload);
          }
          command.send().join();
        });
  }

  public static Long failTask(
      ZeebeClient client,
      String jobType,
      String workerName,
      int numberOfFailures,
      String errorMessage) {
    return handleTasks(
            client,
            jobType,
            workerName,
            numberOfFailures,
            ((jobClient, job) -> {
              final FailJobCommandStep2 failCommand =
                  jobClient.newFailCommand(job.getKey()).retries(job.getRetries() - 1);
              if (errorMessage != null) {
                failCommand.errorMessage(errorMessage);
              }
              failCommand.send().join();
            }))
        .get(0);
  }

  public static Long failTaskWithRetries(
      ZeebeClient client,
      String jobType,
      String workerName,
      int numberOfJobs,
      int numberOfRetries,
      String errorMessage) {
    return handleTasks(
            client,
            jobType,
            workerName,
            numberOfJobs,
            ((jobClient, job) -> {
              final FailJobCommandStep2 failCommand =
                  jobClient.newFailCommand(job.getKey()).retries(numberOfRetries);
              if (errorMessage != null) {
                failCommand.errorMessage(errorMessage);
              }
              failCommand.send().join();
            }))
        .get(0);
  }

  public static Long throwErrorInTask(
      ZeebeClient client,
      String jobType,
      String workerName,
      int numberOfFailures,
      String errorCode,
      String errorMessage) {
    return handleTasks(
            client,
            jobType,
            workerName,
            numberOfFailures,
            ((jobClient, job) -> {
              jobClient
                  .newThrowErrorCommand(job.getKey())
                  .errorCode(errorCode)
                  .errorMessage(errorMessage)
                  .send()
                  .join();
            }))
        .get(0);
  }

  private static List<Long> handleTasks(
      ZeebeClient client,
      String jobType,
      String workerName,
      int jobCount,
      BiConsumer<JobClient, ActivatedJob> jobHandler) {
    final List<Long> jobKeys = new ArrayList<>();
    while (jobKeys.size() < jobCount) {
      client
          .newActivateJobsCommand()
          .jobType(jobType)
          .maxJobsToActivate(jobCount - jobKeys.size())
          .workerName(workerName)
          .timeout(Duration.ofSeconds(2))
          .send()
          .join()
          .getJobs()
          .forEach(
              job -> {
                jobHandler.accept(client, job);
                jobKeys.add(job.getKey());
              });
    }
    return jobKeys;
  }

  public static void resolveIncident(ZeebeClient client, Long jobKey, Long incidentKey) {
    client.newUpdateRetriesCommand(jobKey).retries(3).send().join();
    client.newResolveIncidentCommand(incidentKey).send().join();
  }

  public static void updateVariables(ZeebeClient client, Long scopeKey, String newPayload) {
    client.newSetVariablesCommand(scopeKey).variables(newPayload).local(true).send().join();
  }
}
