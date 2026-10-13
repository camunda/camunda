/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class ProcessInstanceCallHierarchyAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String GRANDPARENT_ID = "call_hierarchy_grandparent";
  private static final String PARENT_ID = "call_hierarchy_parent";
  private static final String CHILD_ID = "call_hierarchy_child";
  private static final String ADMIN = "admin";
  private static final String CHILD_ONLY = "childOnly";
  private static final String WITHOUT_PARENT = "withoutParent";
  private static final String WITHOUT_GRANDPARENT = "withoutGrandparent";

  private static long childProcessInstanceKey;

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"))));

  @UserDefinition
  private static final TestUser CHILD_ONLY_USER =
      new TestUser(
          CHILD_ONLY,
          "password",
          List.of(new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(CHILD_ID))));

  @UserDefinition
  private static final TestUser WITHOUT_PARENT_USER =
      new TestUser(
          WITHOUT_PARENT,
          "password",
          List.of(
              new Permissions(
                  PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(GRANDPARENT_ID, CHILD_ID))));

  @UserDefinition
  private static final TestUser WITHOUT_GRANDPARENT_USER =
      new TestUser(
          WITHOUT_GRANDPARENT,
          "password",
          List.of(
              new Permissions(
                  PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(PARENT_ID, CHILD_ID))));

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    deploy(
        adminClient,
        Bpmn.createExecutableProcess(CHILD_ID)
            .name(CHILD_ID)
            .startEvent()
            .userTask("wait")
            .endEvent()
            .done());
    deploy(
        adminClient,
        Bpmn.createExecutableProcess(PARENT_ID)
            .name(PARENT_ID)
            .startEvent()
            .callActivity("callChild", c -> c.zeebeProcessId(CHILD_ID))
            .endEvent()
            .done());
    deploy(
        adminClient,
        Bpmn.createExecutableProcess(GRANDPARENT_ID)
            .name(GRANDPARENT_ID)
            .startEvent()
            .callActivity("callParent", c -> c.zeebeProcessId(PARENT_ID))
            .endEvent()
            .done());

    adminClient
        .newCreateInstanceCommand()
        .bpmnProcessId(GRANDPARENT_ID)
        .latestVersion()
        .send()
        .join();

    Awaitility.await("all process instances of the hierarchy are exported")
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var items =
                  adminClient
                      .newProcessInstanceSearchRequest()
                      .filter(
                          f ->
                              f.processDefinitionId(
                                  fn -> fn.in(GRANDPARENT_ID, PARENT_ID, CHILD_ID)))
                      .send()
                      .join()
                      .items();
              assertThat(items).hasSize(3);
              childProcessInstanceKey =
                  items.stream()
                      .filter(i -> CHILD_ID.equals(i.getProcessDefinitionId()))
                      .findFirst()
                      .orElseThrow()
                      .getProcessInstanceKey();
            });
  }

  @Test
  void shouldReturnWholeHierarchyForFullyAuthorizedUser(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    assertThat(callHierarchyNames(adminClient))
        .containsExactly(GRANDPARENT_ID, PARENT_ID, CHILD_ID);
  }

  @Test
  void shouldNotReturnUnauthorizedAncestors(@Authenticated(CHILD_ONLY) final CamundaClient client) {
    assertThat(callHierarchyNames(client)).containsExactly(CHILD_ID);
  }

  @Test
  void shouldNotReturnUnauthorizedMiddleOfHierarchy(
      @Authenticated(WITHOUT_PARENT) final CamundaClient client) {
    assertThat(callHierarchyNames(client)).containsExactly(GRANDPARENT_ID, CHILD_ID);
  }

  @Test
  void shouldNotReturnUnauthorizedTopOfHierarchy(
      @Authenticated(WITHOUT_GRANDPARENT) final CamundaClient client) {
    assertThat(callHierarchyNames(client)).containsExactly(PARENT_ID, CHILD_ID);
  }

  private static List<String> callHierarchyNames(final CamundaClient client) {
    return client
        .newProcessInstanceGetCallHierarchyRequest(childProcessInstanceKey)
        .send()
        .join()
        .stream()
        .map(e -> e.getProcessDefinitionName())
        .toList();
  }

  private static void deploy(final CamundaClient client, final BpmnModelInstance model) {
    final String processId = model.getModelElementsByType(Process.class).iterator().next().getId();
    client.newDeployResourceCommand().addProcessModel(model, processId + ".bpmn").send().join();
  }
}
