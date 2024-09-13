/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.identity;

import static io.camunda.operate.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.property.IdentityProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.rest.dto.ProcessGroupDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionGroupDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.security.SecurityContextWrapper;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import io.camunda.webapps.schema.entities.operate.dmn.definition.DecisionDefinitionEntity;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      PermissionsService.class,
      SecurityContextWrapper.class
    },
    properties = {OperateProperties.PREFIX + ".identity.issuerUrl = http://some.issuer.url"})
@ActiveProfiles({IDENTITY_AUTH_PROFILE, "test"})
public class PermissionsIT {

  private static final String READ = "READ";
  private static final String DELETE = "DELETE";
  private static final String UPDATE = "UPDATE";

  @MockBean private OperateProperties operateProperties;
  @Autowired private PermissionsService permissionsService;

  @Test
  public void testProcessesGrouped() {
    // given
    final String demoProcessId = "demoProcess";
    final String orderProcessId = "orderProcess";
    final String loanProcessId = "loanProcess";

    final Map<ProcessStore.ProcessKey, List<ProcessEntity>> processesGrouped =
        new LinkedHashMap<>();
    processesGrouped.put(
        new ProcessStore.ProcessKey(demoProcessId, null),
        Collections.singletonList(new ProcessEntity().setBpmnProcessId(demoProcessId)));
    processesGrouped.put(
        new ProcessStore.ProcessKey(orderProcessId, null),
        Collections.singletonList(new ProcessEntity().setBpmnProcessId(orderProcessId)));
    processesGrouped.put(
        new ProcessStore.ProcessKey(loanProcessId, null),
        Collections.singletonList(new ProcessEntity().setBpmnProcessId(loanProcessId)));

    // when
    registerAuthorizations(
        List.of(
            new IdentityAuthorization()
                .setResourceKey(demoProcessId)
                .setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
                .setPermissions(new HashSet<>(Arrays.asList(READ, DELETE, UPDATE))),
            new IdentityAuthorization()
                .setResourceKey(orderProcessId)
                .setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
                .setPermissions(new HashSet<>(Arrays.asList(READ, DELETE)))));

    final List<ProcessGroupDto> processGroupDtos =
        ProcessGroupDto.createFrom(processesGrouped, permissionsService);

    // then
    assertThat(processGroupDtos).hasSize(3);

    final ProcessGroupDto demoProcessProcessGroup =
        processGroupDtos.stream()
            .filter(x -> x.getBpmnProcessId().equals(demoProcessId))
            .findFirst()
            .get();
    assertThat(demoProcessProcessGroup.getPermissions()).hasSize(3);
    assertThat(demoProcessProcessGroup.getPermissions())
        .containsExactlyInAnyOrder(READ, DELETE, UPDATE);

    final ProcessGroupDto orderProcessProcessGroup =
        processGroupDtos.stream()
            .filter(x -> x.getBpmnProcessId().equals(orderProcessId))
            .findFirst()
            .get();
    assertThat(orderProcessProcessGroup.getPermissions()).hasSize(2);
    assertThat(orderProcessProcessGroup.getPermissions()).containsExactlyInAnyOrder(READ, DELETE);

    final ProcessGroupDto loanProcessProcessGroup =
        processGroupDtos.stream()
            .filter(x -> x.getBpmnProcessId().equals(loanProcessId))
            .findFirst()
            .get();
    assertThat(loanProcessProcessGroup.getPermissions()).isEmpty();
  }

  @Test
  public void testProcessesGroupedWithWildcardPermission() {
    // given
    final String demoProcessId = "demoProcess";
    final String orderProcessId = "orderProcess";
    final String loanProcessId = "loanProcess";

    final Map<ProcessStore.ProcessKey, List<ProcessEntity>> processesGrouped =
        new LinkedHashMap<>();
    processesGrouped.put(
        new ProcessStore.ProcessKey(demoProcessId, null),
        List.of(new ProcessEntity().setBpmnProcessId(demoProcessId)));
    processesGrouped.put(
        new ProcessStore.ProcessKey(orderProcessId, null),
        List.of(new ProcessEntity().setBpmnProcessId(orderProcessId)));
    processesGrouped.put(
        new ProcessStore.ProcessKey(loanProcessId, null),
        List.of(new ProcessEntity().setBpmnProcessId(loanProcessId)));

    // when
    registerAuthorizations(
        List.of(
            new IdentityAuthorization()
                .setResourceKey(demoProcessId)
                .setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
                .setPermissions(new HashSet<>(List.of(DELETE))),
            new IdentityAuthorization()
                .setResourceKey(orderProcessId)
                .setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
                .setPermissions(new HashSet<>(List.of(UPDATE))),
            new IdentityAuthorization()
                .setResourceKey(PermissionsService.RESOURCE_KEY_ALL)
                .setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ)))));

    final List<ProcessGroupDto> processGroupDtos =
        ProcessGroupDto.createFrom(processesGrouped, permissionsService);

    // then
    assertThat(processGroupDtos).hasSize(3);

    final ProcessGroupDto demoProcessProcessGroup =
        processGroupDtos.stream()
            .filter(x -> x.getBpmnProcessId().equals(demoProcessId))
            .findFirst()
            .get();
    assertThat(demoProcessProcessGroup.getPermissions()).hasSize(2);
    assertThat(demoProcessProcessGroup.getPermissions()).containsExactlyInAnyOrder(READ, DELETE);

    final ProcessGroupDto orderProcessProcessGroup =
        processGroupDtos.stream()
            .filter(x -> x.getBpmnProcessId().equals(orderProcessId))
            .findFirst()
            .get();
    assertThat(orderProcessProcessGroup.getPermissions()).hasSize(2);
    assertThat(orderProcessProcessGroup.getPermissions()).containsExactlyInAnyOrder(READ, UPDATE);

    final ProcessGroupDto loanProcessProcessGroup =
        processGroupDtos.stream()
            .filter(x -> x.getBpmnProcessId().equals(loanProcessId))
            .findFirst()
            .get();
    assertThat(loanProcessProcessGroup.getPermissions()).hasSize(1);
    assertThat(loanProcessProcessGroup.getPermissions()).containsExactlyInAnyOrder(READ);
  }

  @Test
  public void testDecisionsGrouped() {
    // given
    final String demoDecisionId = "demoDecision";
    final String orderDecisionId = "orderDecision";
    final String loanDecisionId = "loanDecision";

    final Map<String, List<DecisionDefinitionEntity>> decisionsGrouped = new LinkedHashMap<>();
    decisionsGrouped.put(
        demoDecisionId,
        Collections.singletonList(new DecisionDefinitionEntity().setDecisionId(demoDecisionId)));
    decisionsGrouped.put(
        orderDecisionId,
        Collections.singletonList(new DecisionDefinitionEntity().setDecisionId(orderDecisionId)));
    decisionsGrouped.put(
        loanDecisionId,
        Collections.singletonList(new DecisionDefinitionEntity().setDecisionId(loanDecisionId)));

    // when
    registerAuthorizations(
        List.of(
            new IdentityAuthorization()
                .setResourceKey(demoDecisionId)
                .setResourceType(PermissionsService.RESOURCE_TYPE_DECISION_DEFINITION)
                .setPermissions(new HashSet<>(Arrays.asList(READ, DELETE, UPDATE))),
            new IdentityAuthorization()
                .setResourceKey(orderDecisionId)
                .setResourceType(PermissionsService.RESOURCE_TYPE_DECISION_DEFINITION)
                .setPermissions(new HashSet<>(Arrays.asList(READ, DELETE)))));

    final List<DecisionGroupDto> decisionGroupDtos =
        DecisionGroupDto.createFrom(decisionsGrouped, permissionsService);

    // then
    assertThat(decisionGroupDtos).hasSize(3);

    final DecisionGroupDto demoDecisionProcessGroup =
        decisionGroupDtos.stream()
            .filter(x -> x.getDecisionId().equals(demoDecisionId))
            .findFirst()
            .get();
    assertThat(demoDecisionProcessGroup.getPermissions()).hasSize(3);
    assertThat(demoDecisionProcessGroup.getPermissions())
        .containsExactlyInAnyOrder(READ, DELETE, UPDATE);

    final DecisionGroupDto orderDecisionProcessGroup =
        decisionGroupDtos.stream()
            .filter(x -> x.getDecisionId().equals(orderDecisionId))
            .findFirst()
            .get();
    assertThat(orderDecisionProcessGroup.getPermissions()).hasSize(2);
    assertThat(orderDecisionProcessGroup.getPermissions()).containsExactlyInAnyOrder(READ, DELETE);

    final DecisionGroupDto loanDecisionProcessGroup =
        decisionGroupDtos.stream()
            .filter(x -> x.getDecisionId().equals(loanDecisionId))
            .findFirst()
            .get();
    assertThat(loanDecisionProcessGroup.getPermissions()).isEmpty();
  }

  @Test
  public void testDecisionsGroupedWithWildcardPermission() {
    // given
    final String demoDecisionId = "demoDecision";
    final String orderDecisionId = "orderDecision";
    final String loanDecisionId = "loanDecision";

    final Map<String, List<DecisionDefinitionEntity>> decisionsGrouped = new LinkedHashMap<>();
    decisionsGrouped.put(
        demoDecisionId,
        Collections.singletonList(new DecisionDefinitionEntity().setDecisionId(demoDecisionId)));
    decisionsGrouped.put(
        orderDecisionId,
        Collections.singletonList(new DecisionDefinitionEntity().setDecisionId(orderDecisionId)));
    decisionsGrouped.put(
        loanDecisionId,
        Collections.singletonList(new DecisionDefinitionEntity().setDecisionId(loanDecisionId)));

    // when
    registerAuthorizations(
        List.of(
            new IdentityAuthorization()
                .setResourceKey(demoDecisionId)
                .setResourceType(PermissionsService.RESOURCE_TYPE_DECISION_DEFINITION)
                .setPermissions(new HashSet<>(List.of(DELETE))),
            new IdentityAuthorization()
                .setResourceKey(orderDecisionId)
                .setResourceType(PermissionsService.RESOURCE_TYPE_DECISION_DEFINITION)
                .setPermissions(new HashSet<>(List.of(UPDATE))),
            new IdentityAuthorization()
                .setResourceKey(PermissionsService.RESOURCE_KEY_ALL)
                .setResourceType(PermissionsService.RESOURCE_TYPE_DECISION_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ)))));

    final List<DecisionGroupDto> decisionGroupDtos =
        DecisionGroupDto.createFrom(decisionsGrouped, permissionsService);

    // then
    assertThat(decisionGroupDtos).hasSize(3);

    final DecisionGroupDto demoDecisionProcessGroup =
        decisionGroupDtos.stream()
            .filter(x -> x.getDecisionId().equals(demoDecisionId))
            .findFirst()
            .get();
    assertThat(demoDecisionProcessGroup.getPermissions()).hasSize(2);
    assertThat(demoDecisionProcessGroup.getPermissions()).containsExactlyInAnyOrder(READ, DELETE);

    final DecisionGroupDto orderDecisionProcessGroup =
        decisionGroupDtos.stream()
            .filter(x -> x.getDecisionId().equals(orderDecisionId))
            .findFirst()
            .get();
    assertThat(orderDecisionProcessGroup.getPermissions()).hasSize(2);
    assertThat(orderDecisionProcessGroup.getPermissions()).containsExactlyInAnyOrder(READ, UPDATE);

    final DecisionGroupDto loanDecisionProcessGroup =
        decisionGroupDtos.stream()
            .filter(x -> x.getDecisionId().equals(loanDecisionId))
            .findFirst()
            .get();
    assertThat(loanDecisionProcessGroup.getPermissions()).hasSize(1);
    assertThat(loanDecisionProcessGroup.getPermissions()).containsExactlyInAnyOrder(READ);
  }

  @Test
  public void testProcessInstances() {
    // given
    final String demoProcessId = "demoProcess";

    final ProcessInstanceForListViewEntity entity = new ProcessInstanceForListViewEntity();
    entity.setBpmnProcessId(demoProcessId);

    // when
    registerAuthorizations(
        List.of(
            new IdentityAuthorization()
                .setResourceKey(demoProcessId)
                .setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ, DELETE, UPDATE)))));

    final ListViewProcessInstanceDto listViewProcessInstanceDto =
        ListViewProcessInstanceDto.createFrom(
            entity, List.of(), List.of(), permissionsService, new ObjectMapper());

    // then
    assertThat(listViewProcessInstanceDto.getPermissions()).hasSize(3);
    assertThat(listViewProcessInstanceDto.getPermissions())
        .containsExactlyInAnyOrder(READ, DELETE, UPDATE);
  }

  @Test
  public void testProcessInstancesWithWildcardPermission() {
    // given
    final String demoProcessId = "demoProcess";

    final ProcessInstanceForListViewEntity entity = new ProcessInstanceForListViewEntity();
    entity.setBpmnProcessId(demoProcessId);

    // when
    registerAuthorizations(
        List.of(
            new IdentityAuthorization()
                .setResourceKey(demoProcessId)
                .setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
                .setPermissions(new HashSet<>(List.of(UPDATE))),
            new IdentityAuthorization()
                .setResourceKey(PermissionsService.RESOURCE_KEY_ALL)
                .setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ)))));

    final ListViewProcessInstanceDto listViewProcessInstanceDto =
        ListViewProcessInstanceDto.createFrom(
            entity, List.of(), List.of(), permissionsService, new ObjectMapper());

    // then
    assertThat(listViewProcessInstanceDto.getPermissions()).hasSize(2);
    assertThat(listViewProcessInstanceDto.getPermissions()).containsExactlyInAnyOrder(READ, UPDATE);
  }

  @Test
  public void testHasPermissionForProcessWhenPermissionsDisabled() {
    // given
    final String bpmnProcessId = "processId";
    final IdentityPermission permission = IdentityPermission.READ;

    // when
    Mockito.when(operateProperties.getIdentity())
        .thenReturn(new IdentityProperties().setResourcePermissionsEnabled(false));
    final boolean hasPermission =
        permissionsService.hasPermissionForProcess(bpmnProcessId, permission);

    // then
    assertThat(hasPermission).isTrue();
  }

  @Test
  public void testNoPermissionForProcessWhenPermissionsNotFound() {
    // given
    final String bpmnProcessId = "processId";
    final IdentityPermission permission = IdentityPermission.READ;

    // when
    Mockito.when(operateProperties.getIdentity())
        .thenReturn(new IdentityProperties().setResourcePermissionsEnabled(true));
    registerAuthorizations(List.of());
    final boolean hasPermission =
        permissionsService.hasPermissionForProcess(bpmnProcessId, permission);

    // then
    assertThat(hasPermission).isFalse();
  }

  @Test
  public void testHasPermissionForProcessWhenPermissionsFound() {
    // given
    final String bpmnProcessId = "processId";
    final IdentityPermission permission = IdentityPermission.READ;

    // when
    Mockito.when(operateProperties.getIdentity())
        .thenReturn(new IdentityProperties().setResourcePermissionsEnabled(true));
    registerAuthorizations(
        List.of(
            new IdentityAuthorization()
                .setResourceKey(bpmnProcessId)
                .setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ)))));
    final boolean hasPermission =
        permissionsService.hasPermissionForProcess(bpmnProcessId, permission);

    // then
    assertThat(hasPermission).isTrue();
  }

  @Test
  public void testHasPermissionForDecisionWhenPermissionsDisabled() {
    // given
    final String bpmnDecisionId = "decisionId";
    final IdentityPermission permission = IdentityPermission.READ;

    // when
    Mockito.when(operateProperties.getIdentity())
        .thenReturn(new IdentityProperties().setResourcePermissionsEnabled(false));
    final boolean hasPermission =
        permissionsService.hasPermissionForDecision(bpmnDecisionId, permission);

    // then
    assertThat(hasPermission).isTrue();
  }

  @Test
  public void testNoPermissionForDecisionWhenPermissionsNotFound() {
    // given
    final String bpmnDecisionId = "decisionId";
    final IdentityPermission permission = IdentityPermission.READ;

    // when
    Mockito.when(operateProperties.getIdentity())
        .thenReturn(new IdentityProperties().setResourcePermissionsEnabled(true));
    registerAuthorizations(List.of());
    final boolean hasPermission =
        permissionsService.hasPermissionForDecision(bpmnDecisionId, permission);

    // then
    assertThat(hasPermission).isFalse();
  }

  @Test
  public void testHasPermissionForDecisionWhenPermissionsFound() {
    // given
    final String bpmnDecisionId = "decisionId";
    final IdentityPermission permission = IdentityPermission.READ;

    // when
    Mockito.when(operateProperties.getIdentity())
        .thenReturn(new IdentityProperties().setResourcePermissionsEnabled(true));
    registerAuthorizations(
        List.of(
            new IdentityAuthorization()
                .setResourceKey(bpmnDecisionId)
                .setResourceType(PermissionsService.RESOURCE_TYPE_DECISION_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ)))));
    final boolean hasPermission =
        permissionsService.hasPermissionForDecision(bpmnDecisionId, permission);

    // then
    assertThat(hasPermission).isTrue();
  }

  @Test
  public void testGetProcessesWithPermissionWhenPermissionsDisabled() {
    // given
    final String bpmnProcessId = "processId";
    final IdentityPermission permission = IdentityPermission.READ;

    // when
    Mockito.when(operateProperties.getIdentity())
        .thenReturn(new IdentityProperties().setResourcePermissionsEnabled(false));
    registerAuthorizations(
        List.of(
            new IdentityAuthorization()
                .setResourceKey(bpmnProcessId)
                .setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ)))));
    final PermissionsService.ResourcesAllowed resourcesAllowed =
        permissionsService.getProcessesWithPermission(permission);

    // then
    assertThat(resourcesAllowed.isAll()).isTrue();
  }

  @Test
  public void testGetProcessesWithPermissionWhenAllProcessesAllowed() {
    // given
    final IdentityPermission permission = IdentityPermission.READ;

    // when
    Mockito.when(operateProperties.getIdentity())
        .thenReturn(new IdentityProperties().setResourcePermissionsEnabled(true));
    registerAuthorizations(
        List.of(
            new IdentityAuthorization()
                .setResourceKey(PermissionsService.RESOURCE_KEY_ALL)
                .setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ)))));
    final PermissionsService.ResourcesAllowed resourcesAllowed =
        permissionsService.getProcessesWithPermission(permission);

    // then
    assertThat(resourcesAllowed.isAll()).isTrue();
  }

  @Test
  public void testGetProcessesWithPermissionWhenSpecificProcessesAllowed() {
    // given
    final String bpmnProcessId1 = "processId1";
    final String bpmnProcessId2 = "processId2";
    final String bpmnProcessId3 = "processId3";
    final IdentityPermission permission = IdentityPermission.READ;

    // when
    Mockito.when(operateProperties.getIdentity())
        .thenReturn(new IdentityProperties().setResourcePermissionsEnabled(true));
    registerAuthorizations(
        List.of(
            new IdentityAuthorization()
                .setResourceKey(bpmnProcessId1)
                .setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ))),
            new IdentityAuthorization()
                .setResourceKey(bpmnProcessId2)
                .setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ, UPDATE))),
            new IdentityAuthorization()
                .setResourceKey(bpmnProcessId3)
                .setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
                .setPermissions(new HashSet<>(List.of(DELETE)))));
    final PermissionsService.ResourcesAllowed resourcesAllowed =
        permissionsService.getProcessesWithPermission(permission);

    // then
    assertThat(resourcesAllowed.isAll()).isFalse();
    assertThat(resourcesAllowed.getIds()).containsExactlyInAnyOrder(bpmnProcessId1, bpmnProcessId2);
  }

  @Test
  public void testGetProcessesByPermissionWhenAllProcessesAllowed() {
    // given
    final IdentityPermission permission = IdentityPermission.READ;

    // when
    Mockito.when(operateProperties.getIdentity())
        .thenReturn(new IdentityProperties().setResourcePermissionsEnabled(false));
    final var resourcesAllowed = permissionsService.getProcessesWithPermission(permission);

    // then
    assertThat(resourcesAllowed).isEqualTo(PermissionsService.ResourcesAllowed.all());
  }

  @Test
  public void testGetProcessesByPermissionWhenSpecificProcessesAllowed() {
    // given
    final String bpmnProcessId1 = "processId1";
    final String bpmnProcessId2 = "processId2";
    final IdentityPermission permission = IdentityPermission.READ;

    // when
    Mockito.when(operateProperties.getIdentity())
        .thenReturn(new IdentityProperties().setResourcePermissionsEnabled(true));
    registerAuthorizations(
        List.of(
            new IdentityAuthorization()
                .setResourceKey(bpmnProcessId1)
                .setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ))),
            new IdentityAuthorization()
                .setResourceKey(bpmnProcessId2)
                .setResourceType(PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ)))));
    final var resourcesAllowed = permissionsService.getProcessesWithPermission(permission);

    // then
    assertThat(resourcesAllowed.getIds()).containsExactlyInAnyOrder(bpmnProcessId1, bpmnProcessId2);
  }

  @Test
  public void testGetDecisionsWithPermissionWhenPermissionsDisabled() {
    // given
    final String decisionId = "decisionId";
    final IdentityPermission permission = IdentityPermission.READ;

    // when
    Mockito.when(operateProperties.getIdentity())
        .thenReturn(new IdentityProperties().setResourcePermissionsEnabled(false));
    registerAuthorizations(
        List.of(
            new IdentityAuthorization()
                .setResourceKey(decisionId)
                .setResourceType(PermissionsService.RESOURCE_TYPE_DECISION_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ)))));
    final PermissionsService.ResourcesAllowed resourcesAllowed =
        permissionsService.getDecisionsWithPermission(permission);

    // then
    assertThat(resourcesAllowed.isAll()).isTrue();
  }

  @Test
  public void testGetDecisionsWithPermissionWhenAllDecisionsAllowed() {
    // given
    final IdentityPermission permission = IdentityPermission.READ;

    // when
    Mockito.when(operateProperties.getIdentity())
        .thenReturn(new IdentityProperties().setResourcePermissionsEnabled(true));
    registerAuthorizations(
        List.of(
            new IdentityAuthorization()
                .setResourceKey(PermissionsService.RESOURCE_KEY_ALL)
                .setResourceType(PermissionsService.RESOURCE_TYPE_DECISION_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ)))));
    final PermissionsService.ResourcesAllowed resourcesAllowed =
        permissionsService.getDecisionsWithPermission(permission);

    // then
    assertThat(resourcesAllowed.isAll()).isTrue();
  }

  @Test
  public void testGetDecisionsWithPermissionWhenSpecificDecisionsAllowed() {
    // given
    final String decisionId1 = "decisionId1";
    final String decisionId2 = "decisionId2";
    final String decisionId3 = "decisionId3";
    final IdentityPermission permission = IdentityPermission.READ;

    // when
    Mockito.when(operateProperties.getIdentity())
        .thenReturn(new IdentityProperties().setResourcePermissionsEnabled(true));
    registerAuthorizations(
        List.of(
            new IdentityAuthorization()
                .setResourceKey(decisionId1)
                .setResourceType(PermissionsService.RESOURCE_TYPE_DECISION_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ))),
            new IdentityAuthorization()
                .setResourceKey(decisionId2)
                .setResourceType(PermissionsService.RESOURCE_TYPE_DECISION_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ, UPDATE))),
            new IdentityAuthorization()
                .setResourceKey(decisionId3)
                .setResourceType(PermissionsService.RESOURCE_TYPE_DECISION_DEFINITION)
                .setPermissions(new HashSet<>(List.of(DELETE)))));
    final PermissionsService.ResourcesAllowed resourcesAllowed =
        permissionsService.getDecisionsWithPermission(permission);

    // then
    assertThat(resourcesAllowed.isAll()).isFalse();
    assertThat(resourcesAllowed.getIds()).containsExactlyInAnyOrder(decisionId1, decisionId2);
  }

  @Test
  public void testGetDecisionsByPermissionWhenAllDecisionsAllowed() {
    // given
    final IdentityPermission permission = IdentityPermission.READ;

    // when
    Mockito.when(operateProperties.getIdentity())
        .thenReturn(new IdentityProperties().setResourcePermissionsEnabled(false));
    final var resourcesAllowed = permissionsService.getDecisionsWithPermission(permission);

    // then
    assertThat(resourcesAllowed).isEqualTo(PermissionsService.ResourcesAllowed.all());
  }

  @Test
  public void testGetDecisionsByPermissionWhenSpecificDecisionsAllowed() {
    // given
    final String decisionId1 = "decisionId1";
    final String decisionId2 = "decisionId2";
    final IdentityPermission permission = IdentityPermission.READ;

    // when
    Mockito.when(operateProperties.getIdentity())
        .thenReturn(new IdentityProperties().setResourcePermissionsEnabled(true));
    registerAuthorizations(
        List.of(
            new IdentityAuthorization()
                .setResourceKey(decisionId1)
                .setResourceType(PermissionsService.RESOURCE_TYPE_DECISION_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ))),
            new IdentityAuthorization()
                .setResourceKey(decisionId2)
                .setResourceType(PermissionsService.RESOURCE_TYPE_DECISION_DEFINITION)
                .setPermissions(new HashSet<>(List.of(READ)))));
    final var resourcesAllowed = permissionsService.getDecisionsWithPermission(permission);

    // then
    assertThat(resourcesAllowed.getIds()).containsExactlyInAnyOrder(decisionId1, decisionId2);
  }

  private void registerAuthorizations(final List<IdentityAuthorization> authorizations) {

    final IdentityAuthentication authentication = Mockito.mock(IdentityAuthentication.class);
    Mockito.when(authentication.getAuthorizations()).thenReturn(authorizations);
    final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
    SecurityContextHolder.setContext(securityContext);
  }
}
