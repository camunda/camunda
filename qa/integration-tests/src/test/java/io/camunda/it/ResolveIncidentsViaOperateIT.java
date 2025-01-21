/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import static io.camunda.client.protocol.rest.ResourceTypeEnum.PROCESS_DEFINITION;
import static io.camunda.it.utils.CamundaClientTestRequestHelper.deployProcessAndStartInstance;
import static io.camunda.it.utils.CamundaClientTestRequestHelper.failJobAndWaitForIncident;
import static io.camunda.it.utils.CamundaClientTestRequestHelper.getUserTasksForProcessInstance;
import static io.camunda.it.utils.CamundaClientTestRequestHelper.waitForIncidentResolved;
import static io.camunda.it.utils.CamundaClientTestRequestHelper.waitForJobActivation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.application.Profile;
import io.camunda.client.CamundaClient;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.it.exporter.ExporterTestUtil;
import io.camunda.operate.webapp.elasticsearch.writer.BatchOperationWriter;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import io.camunda.qa.util.cluster.TestRestOperateClient;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.it.util.AuthorizationsUtil.Permissions;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.util.List;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ZeebeIntegration
@AutoCloseResources
@ExtendWith(MockitoExtension.class)
public class ResolveIncidentsViaOperateIT {

  public static final String DEFAULT_USER = "testuser";
  private static final String TASK_LISTENER_BPMN_CLASSPATH =
      "process/process-with-completing-user-task-listener.bpmn";
  private static final String LISTENER_JOB_TYPE = "complete_listener";
  private static final String TASK_LISTENER_PROCESS_ID = "task-listener-process";
  @AutoCloseResource private CamundaClient camundaClient;
  @AutoCloseResource private TestRestOperateClient operateClient;

  @Mock private UserService userService;
  private BatchOperationWriter batchOperationWriter;

  private OperationExecutor operationExecutor;

  @TestZeebe
  private final TestStandaloneCamunda standaloneCamunda =
      new TestStandaloneCamunda().withCamundaExporter().withAdditionalProfile(Profile.AUTH_BASIC);

  @BeforeEach
  public void before() {
    final var authorizationsUtil =
        AuthorizationsUtil.create(
            standaloneCamunda, standaloneCamunda.getElasticSearchHostAddress());
    camundaClient =
        authorizationsUtil.createUserAndClient(
            DEFAULT_USER,
            "password",
            new Permissions(
                PROCESS_DEFINITION, PermissionTypeEnum.CREATE_PROCESS_INSTANCE, List.of("*")));
    operateClient = standaloneCamunda.newOperateClient(DEFAULT_USER, "password");
    Mockito.when(userService.getCurrentUser())
        .thenReturn(new UserDto().setDisplayName(DEFAULT_USER));
    batchOperationWriter = standaloneCamunda.bean(BatchOperationWriter.class);
    ReflectionTestUtils.setField(batchOperationWriter, "userService", userService);
    operationExecutor = standaloneCamunda.bean(OperationExecutor.class);
  }

  @Test
  public void shouldResolveIncident() {
    // Deploy and start task listener process
    final long processInstanceKey =
        deployProcessAndStartInstance(
            camundaClient, TASK_LISTENER_BPMN_CLASSPATH, TASK_LISTENER_PROCESS_ID);
    ExporterTestUtil.waitForProcessTasks(camundaClient, processInstanceKey);
    final var userTask =
        getUserTasksForProcessInstance(camundaClient, processInstanceKey).getFirst();

    // Trigger complete user task and activate listener job
    camundaClient.newUserTaskCompleteCommand(userTask.getUserTaskKey()).send();
    final long jobKey = waitForJobActivation(camundaClient, LISTENER_JOB_TYPE);

    // fail job with no retries left via Zeebe API and wait for incident
    failJobAndWaitForIncident(camundaClient, jobKey, processInstanceKey);

    // resolve via BatchOperationWriter
    // Note: Operate API is not used here because new Identity permissions are not implemented
    // for the internal API yet.
    batchOperationWriter.scheduleSingleOperation(
        processInstanceKey,
        new CreateOperationRequestDto().setOperationType(OperationType.RESOLVE_INCIDENT));
    try {
      Thread.sleep(2000);
    } catch (final InterruptedException e) {
      throw new RuntimeException(e);
    }
    executeOneBatch();
    // assert incident gets resolved
    waitForIncidentResolved(camundaClient, processInstanceKey);
    final boolean incidentInOperate =
        operateClient
            .getProcessInstanceWith(processInstanceKey)
            .get()
            .processInstances()
            .getFirst()
            .getIncident();
    assertThat(incidentInOperate).isFalse();
  }

  protected void executeOneBatch() {
    try {
      final List<Future<?>> futures = operationExecutor.executeOneBatch();
      // wait till all scheduled tasks are executed
      for (final Future f : futures) {
        f.get();
      }
    } catch (final Exception e) {
      fail(e.getMessage(), e);
    }
  }
}
