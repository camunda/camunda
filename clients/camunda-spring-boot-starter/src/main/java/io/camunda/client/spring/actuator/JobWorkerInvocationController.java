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
package io.camunda.client.spring.actuator;

import static java.util.Optional.ofNullable;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.impl.response.ActivatedJobImpl;
import io.camunda.client.jobhandling.JobHandlerFactory;
import io.camunda.client.jobhandling.JobHandlerFactory.JobHandlerFactoryContext;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.protocol.rest.ActivatedJobResult;
import io.camunda.client.protocol.rest.JobKindEnum;
import io.camunda.client.protocol.rest.JobListenerEventTypeEnum;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

@WebEndpoint(id = "jobworkerinvocation")
public class JobWorkerInvocationController {
  private final CamundaClient camundaClient;
  private final JobWorkerManager jobWorkerManager;

  public JobWorkerInvocationController(
      final CamundaClient camundaClient, final JobWorkerManager jobWorkerManager) {
    this.camundaClient = camundaClient;
    this.jobWorkerManager = jobWorkerManager;
  }

  @WriteOperation
  public Object invoke(
      @Selector final String type,
      @Nullable final String variables,
      @Nullable final Long jobKey,
      @Nullable final Long processInstanceKey,
      @Nullable final Long processDefinitionKey,
      @Nullable final Integer processDefinitionVersion,
      @Nullable final String processDefinitionId,
      @Nullable final String elementId,
      @Nullable final Long elementInstanceKey,
      @Nullable final String customHeaders,
      @Nullable final Integer retries,
      @Nullable final Long deadline,
      @Nullable final String tenantId,
      @Nullable final String tags)
      throws Exception {
    final InvocationCamundaClient invocationCamundaClient =
        new InvocationCamundaClient(camundaClient.getConfiguration().getJsonMapper());
    final JobWorkerValue jobWorker = jobWorkerManager.getJobWorker(type);
    final JobHandlerFactory jobHandlerFactory = jobWorkerManager.getJobHandlerFactory(type);
    final JobHandler jobHandler =
        jobHandlerFactory.getJobHandler(
            new JobHandlerFactoryContext(jobWorker, invocationCamundaClient));
    final ActivatedJob activatedJob =
        new ActivatedJobImpl(
            camundaClient.getConfiguration().getJsonMapper(),
            new ActivatedJobResult()
                .jobKey(String.valueOf(ofNullable(jobKey).orElse(0L)))
                .type(type)
                .processInstanceKey(String.valueOf(ofNullable(processInstanceKey).orElse(0L)))
                .processDefinitionKey(String.valueOf(ofNullable(processDefinitionKey).orElse(0L)))
                .processDefinitionVersion(
                    processDefinitionVersion != null ? processDefinitionVersion : 0)
                .processDefinitionId(ofNullable(processDefinitionId).orElse("<invocation>"))
                .elementId(ofNullable(elementId).orElse("<invocation>"))
                .elementInstanceKey(String.valueOf(ofNullable(elementInstanceKey).orElse(0L)))
                .customHeaders(
                    camundaClient
                        .getConfiguration()
                        .getJsonMapper()
                        .fromJsonAsMap(ofNullable(customHeaders).orElse("{}")))
                .worker(jobWorker.getName().value())
                .retries(ofNullable(retries).orElse(1))
                .deadline(
                    ofNullable(deadline).orElse(Instant.now().plusSeconds(300).toEpochMilli()))
                .variables(
                    camundaClient
                        .getConfiguration()
                        .getJsonMapper()
                        .fromJsonAsMap(ofNullable(variables).orElse("{}")))
                .kind(JobKindEnum.UNKNOWN_DEFAULT_OPEN_API)
                .listenerEventType(JobListenerEventTypeEnum.UNSPECIFIED)
                .tenantId(ofNullable(tenantId).orElse("<default>"))
                .tags(
                    ofNullable(tags)
                        .map(
                            t ->
                                Arrays.stream(t.split(","))
                                    .map(String::trim)
                                    .collect(Collectors.toSet()))
                        .orElse(Set.of())));
    jobHandler.handle(invocationCamundaClient, activatedJob);
    return invocationCamundaClient.getCapturedCommands();
  }
}
