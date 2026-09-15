/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.qa.util.multidb.MultiDbPhysicalTenants;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.qa.util.multidb.MultiPhysicalTenantClients;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * The {@code ProcessCache} that the REST layer reads element and process names from is keyed by the
 * numeric process-definition key alone. That key encodes only a partition number and a sequence
 * position, and partition ids restart at 1 in every physical tenant — so the first deployment in
 * one physical tenant and the first in another are handed the <em>same</em> number. Nothing in the
 * key says which tenant it belongs to.
 *
 * <p>Isolation therefore rests entirely on how the cache is constructed: {@code
 * CamundaServicesConfiguration} builds one {@code ProcessCache} instance per physical tenant inside
 * its per-tenant loop, and binds it only to that tenant's services. A refactor that hoisted the
 * cache out of that loop, or keyed a shared cache by the definition key, would compile and pass
 * every single-tenant test while silently serving one tenant's process and element names to
 * another. This test is the guard against that.
 *
 * <p>Two tenants deploy a process that is identical in every field that could disambiguate the two
 * — same BPMN process id, same element ids — and differs only in the two pieces of metadata the
 * cache carries: the process name and the user-task element name. A leak therefore surfaces as one
 * tenant's read returning the other tenant's name, not as a missing or empty value.
 *
 * <p>The reads are run tenant A, then tenant B, then tenant A again. The first pass populates
 * tenant A's cache, so tenant B's pass is the one that would read a poisoned entry; the third pass
 * catches the reverse, tenant B's reads displacing what tenant A had already cached.
 *
 * <p>{@code UserTask.processName} is the field that pins the cache specifically: no exporter
 * persists it, so its value in a REST response can only have come from the {@code ProcessCache}.
 * The element names are asserted as well — they are what the acceptance criteria name, and they
 * hold whichever layer resolved them — but they may be served from the exporter's own per-tenant
 * cache rather than this one.
 */
@MultiDbTest
@MultiDbPhysicalTenants({"tenanta", "tenantb"})
final class ProcessCachePhysicalTenantIsolationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withAuthenticationMethod(AuthenticationMethod.BASIC);

  static MultiPhysicalTenantClients ptClients;

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";

  /**
   * Deliberately the same in both tenants, so that neither the process id nor the element id can
   * disambiguate a cache entry and only the per-tenant cache instance can.
   */
  private static final String PROCESS_ID = Strings.newRandomValidBpmnId();

  private static final String TASK_ID = "shared-user-task";

  private static final String PROCESS_NAME_A = "Process of tenant A";
  private static final String PROCESS_NAME_B = "Process of tenant B";
  private static final String TASK_NAME_A = "Task of tenant A";
  private static final String TASK_NAME_B = "Task of tenant B";

  private static final Duration PROPAGATION_TIMEOUT = Duration.ofSeconds(60);

  @Test
  void shouldResolveOwnProcessMetadataWhenDefinitionKeysCollideAcrossPhysicalTenants() {
    final CamundaClient tenantA = ptClients.admin(TENANT_A);
    final CamundaClient tenantB = ptClients.admin(TENANT_B);

    // given -- the first deployment in either tenant, so both take sequence position 1 on their own
    // partition 1 and are handed the same numeric process-definition key
    final long keyA = deploy(tenantA, PROCESS_NAME_A, TASK_NAME_A);
    final long keyB = deploy(tenantB, PROCESS_NAME_B, TASK_NAME_B);
    assertThat(keyA)
        .as(
            "the two tenants' first deployments must collide numerically, otherwise the cache "
                + "cannot confuse them and this test proves nothing")
        .isEqualTo(keyB);

    final long instanceA = createInstance(tenantA);
    final long instanceB = createInstance(tenantB);

    // when/then -- tenant A reads first and populates its cache under the shared key
    assertReadsOwnMetadata(tenantA, keyA, instanceA, PROCESS_NAME_A, TASK_NAME_A);

    // and -- tenant B, addressing that very same key, is served its own metadata
    assertReadsOwnMetadata(tenantB, keyA, instanceB, PROCESS_NAME_B, TASK_NAME_B);

    // and -- tenant B's reads did not displace what tenant A had cached
    assertReadsOwnMetadata(tenantA, keyA, instanceA, PROCESS_NAME_A, TASK_NAME_A);
  }

  /**
   * Asserts that every REST read which consumes the {@code ProcessCache} returns the given tenant's
   * own metadata: the process definition itself, the user task by search and by key, and the user
   * task's element instance by search and by key.
   */
  private static void assertReadsOwnMetadata(
      final CamundaClient client,
      final long processDefinitionKey,
      final long processInstanceKey,
      final String expectedProcessName,
      final String expectedTaskName) {
    assertThat(client.newProcessDefinitionGetRequest(processDefinitionKey).send().join().getName())
        .isEqualTo(expectedProcessName);

    final UserTask userTask = awaitUserTask(client, processInstanceKey);
    assertThat(userTask.getProcessName()).isEqualTo(expectedProcessName);
    assertThat(userTask.getName()).isEqualTo(expectedTaskName);

    final UserTask userTaskByKey =
        client.newUserTaskGetRequest(userTask.getUserTaskKey()).send().join();
    assertThat(userTaskByKey.getProcessName()).isEqualTo(expectedProcessName);
    assertThat(userTaskByKey.getName()).isEqualTo(expectedTaskName);

    final ElementInstance elementInstance = awaitElementInstance(client, processInstanceKey);
    assertThat(elementInstance.getElementName()).isEqualTo(expectedTaskName);

    final ElementInstance elementInstanceByKey =
        client.newElementInstanceGetRequest(elementInstance.getElementInstanceKey()).send().join();
    assertThat(elementInstanceByKey.getElementName()).isEqualTo(expectedTaskName);
  }

  private static UserTask awaitUserTask(final CamundaClient client, final long processInstanceKey) {
    return Awaitility.await(
            "the user task of process instance %d is exported".formatted(processInstanceKey))
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .until(
            () ->
                client
                    .newUserTaskSearchRequest()
                    .filter(f -> f.processInstanceKey(processInstanceKey).elementId(TASK_ID))
                    .send()
                    .join()
                    .items()
                    .stream()
                    .findFirst()
                    .orElse(null),
            Objects::nonNull);
  }

  private static ElementInstance awaitElementInstance(
      final CamundaClient client, final long processInstanceKey) {
    return Awaitility.await(
            "the user task element instance of process instance %d is exported"
                .formatted(processInstanceKey))
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .until(
            () ->
                client
                    .newElementInstanceSearchRequest()
                    .filter(f -> f.processInstanceKey(processInstanceKey).elementId(TASK_ID))
                    .send()
                    .join()
                    .items()
                    .stream()
                    .findFirst()
                    .orElse(null),
            Objects::nonNull);
  }

  private static long deploy(
      final CamundaClient client, final String processName, final String taskName) {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .name(processName)
            .startEvent()
            .userTask(TASK_ID)
            .zeebeUserTask()
            .name(taskName)
            .endEvent()
            .done();
    return client
        .newDeployResourceCommand()
        .addProcessModel(model, PROCESS_ID + ".bpmn")
        .send()
        .join()
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private static long createInstance(final CamundaClient client) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }
}
