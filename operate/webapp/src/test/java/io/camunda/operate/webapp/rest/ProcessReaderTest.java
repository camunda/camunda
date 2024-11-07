/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.webapp.reader.ProcessReader;
import io.camunda.operate.webapp.rest.dto.ProcessRequestDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.security.permission.PermissionsService.ResourcesAllowed;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import java.util.HashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProcessReaderTest {

  @Mock private ProcessStore mockProcessStore;

  @Mock private PermissionsService mockPermissionsService;

  @Test
  public void testGetDiagram() {
    final ProcessReader underTest = new ProcessReader(mockProcessStore, mockPermissionsService);

    final String expectedString = "diagram";
    when(mockProcessStore.getDiagramByKey(1L)).thenReturn(expectedString);

    final var response = underTest.getDiagram(1L);

    assertThat(response).isEqualTo(expectedString);
    verifyNoInteractions(mockPermissionsService);
    verify(mockProcessStore, times(1)).getDiagramByKey(1L);
  }

  @Test
  public void testGetProcess() {
    final ProcessReader underTest = new ProcessReader(mockProcessStore, mockPermissionsService);

    when(mockProcessStore.getProcessByKey(1L)).thenReturn(new ProcessEntity());

    final var response = underTest.getProcess(1L);

    assertThat(response).isNotNull();
    verifyNoInteractions(mockPermissionsService);
    verify(mockProcessStore, times(1)).getProcessByKey(1L);
  }

  @Test
  public void testGetProcessesGroupedWithNoPermissionsService() {
    final ProcessReader underTest = new ProcessReader(mockProcessStore, null);

    final String tenantId = "tenantId";
    when(mockProcessStore.getProcessesGrouped(tenantId, null)).thenReturn(new HashMap<>());

    final ProcessRequestDto requestDto = new ProcessRequestDto();
    requestDto.setTenantId(tenantId);

    final var response = underTest.getProcessesGrouped(requestDto);

    assertThat(response).isNotNull();
    verifyNoInteractions(mockPermissionsService);
    verify(mockProcessStore, times(1)).getProcessesGrouped(tenantId, null);
  }

  @Test
  public void testGetProcessesGroupedWithNullPermissions() {
    final ProcessReader underTest = new ProcessReader(mockProcessStore, mockPermissionsService);

    when(mockPermissionsService.getProcessesWithPermission(IdentityPermission.READ))
        .thenReturn(null);

    final String tenantId = "tenantId";
    when(mockProcessStore.getProcessesGrouped(tenantId, null)).thenReturn(new HashMap<>());

    final ProcessRequestDto requestDto = new ProcessRequestDto();
    requestDto.setTenantId(tenantId);

    final var response = underTest.getProcessesGrouped(requestDto);

    assertThat(response).isNotNull();
    verify(mockPermissionsService, times(1)).getProcessesWithPermission(IdentityPermission.READ);
    verify(mockProcessStore, times(1)).getProcessesGrouped(tenantId, null);
  }

  @Test
  public void testGetProcessesGroupedWithAllPermissions() {
    final ProcessReader underTest = new ProcessReader(mockProcessStore, mockPermissionsService);

    when(mockPermissionsService.getProcessesWithPermission(IdentityPermission.READ))
        .thenReturn(ResourcesAllowed.all());

    final String tenantId = "tenantId";
    when(mockProcessStore.getProcessesGrouped(tenantId, null)).thenReturn(new HashMap<>());

    final ProcessRequestDto requestDto = new ProcessRequestDto();
    requestDto.setTenantId(tenantId);

    final var response = underTest.getProcessesGrouped(requestDto);

    assertThat(response).isNotNull();
    verify(mockPermissionsService, times(1)).getProcessesWithPermission(IdentityPermission.READ);
    verify(mockProcessStore, times(1)).getProcessesGrouped(tenantId, null);
  }

  @Test
  public void testGetProcessesGroupedWithSomePermissions() {
    final ProcessReader underTest = new ProcessReader(mockProcessStore, mockPermissionsService);

    final Set<String> allowedProcessIds = Set.of("p1, p2");
    when(mockPermissionsService.getProcessesWithPermission(IdentityPermission.READ))
        .thenReturn(ResourcesAllowed.withIds(allowedProcessIds));

    final String tenantId = "tenantId";
    when(mockProcessStore.getProcessesGrouped(tenantId, allowedProcessIds))
        .thenReturn(new HashMap<>());

    final ProcessRequestDto requestDto = new ProcessRequestDto();
    requestDto.setTenantId(tenantId);

    final var response = underTest.getProcessesGrouped(requestDto);

    assertThat(response).isNotNull();
    verify(mockPermissionsService, times(1)).getProcessesWithPermission(IdentityPermission.READ);
    verify(mockProcessStore, times(1)).getProcessesGrouped(tenantId, allowedProcessIds);
  }
}
