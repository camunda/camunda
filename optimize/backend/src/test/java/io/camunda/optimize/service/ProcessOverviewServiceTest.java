/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import static io.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;
import io.camunda.optimize.service.db.reader.ProcessOverviewReader;
import io.camunda.optimize.service.db.writer.ProcessOverviewWriter;
import io.camunda.optimize.service.digest.DigestService;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import jakarta.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ProcessOverviewServiceTest {

  private final DefinitionService definitionService = mock(DefinitionService.class);
  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService =
      mock(DataSourceDefinitionAuthorizationService.class);
  private final ProcessOverviewWriter processOverviewWriter = mock(ProcessOverviewWriter.class);
  private final ProcessOverviewReader processOverviewReader = mock(ProcessOverviewReader.class);
  private final AbstractIdentityService identityService = mock(AbstractIdentityService.class);
  private final KpiService kpiService = mock(KpiService.class);
  private final DigestService digestService = mock(DigestService.class);

  private final ProcessOverviewService processOverviewService =
      new ProcessOverviewService(
          definitionService,
          definitionAuthorizationService,
          processOverviewWriter,
          processOverviewReader,
          identityService,
          kpiService,
          digestService);

  @Test
  void shouldReturnProcessOverviewsWhenUserIsAuthorized() {
    // given
    final String userId = "user123";
    final String locale = "en";
    final String processKey = "processKey1";
    final String processName = "Process 1";

    final List<String> tenants = new ArrayList<>();
    tenants.add("1");

    final DefinitionWithTenantIdsDto definitionDto =
        new DefinitionWithTenantIdsDto(processKey, processName, PROCESS, tenants, Set.of());
    when(definitionService.getAllDefinitionsWithTenants(PROCESS))
        .thenReturn(List.of(definitionDto));
    when(definitionAuthorizationService.isAuthorizedToAccessDefinition(
            userId, PROCESS, processKey, definitionDto.getTenantIds()))
        .thenReturn(true);

    final Map<String, ProcessOverviewDto> overviewMap =
        Map.of(
            processKey,
            new ProcessOverviewDto(
                "owner123", processKey, new ProcessDigestDto(false, Map.of()), Map.of()));
    when(processOverviewReader.getProcessOverviewsByKey(Set.of(processKey)))
        .thenReturn(overviewMap);

    when(identityService.getIdentityNameById("owner123")).thenReturn(Optional.of("Owner Name"));

    // when
    final List<ProcessOverviewResponseDto> result =
        processOverviewService.getAllProcessOverviews(userId, locale);

    // then
    assertThat(result).hasSize(1);
    final ProcessOverviewResponseDto response = result.get(0);
    assertThat(response.getProcessDefinitionKey()).isEqualTo(processKey);
    assertThat(response.getProcessDefinitionName()).isEqualTo(processName);
    assertThat(response.getOwner().getId()).isEqualTo("owner123");
    assertThat(response.getOwner().getName()).isEqualTo("Owner Name");
  }

  @Test
  void shouldUpdateProcessWhenAuthorizedAndValid() {
    // given
    final String userId = "user123";
    final String processKey = "processKey1";
    final String newOwnerId = "owner456";

    final ProcessUpdateDto updateDto =
        new ProcessUpdateDto(newOwnerId, new ProcessDigestRequestDto(true));

    final List<String> tenants = new ArrayList<>();
    tenants.add("1");

    when(definitionService.getProcessDefinitionWithTenants(processKey))
        .thenReturn(
            Optional.of(
                new DefinitionWithTenantIdsDto(
                    processKey, "Process Name", PROCESS, tenants, Set.of())));
    when(definitionAuthorizationService.isAuthorizedToAccessDefinition(any(), any(), any(), any()))
        .thenReturn(true);

    when(identityService.getUserById(newOwnerId)).thenReturn(Optional.of(new UserDto(newOwnerId)));
    when(identityService.isUserAuthorizedToAccessIdentity(
            userId, new IdentityDto(newOwnerId, IdentityType.USER)))
        .thenReturn(true);

    // when
    processOverviewService.updateProcess(userId, processKey, updateDto);

    // then
    verify(processOverviewWriter).updateProcessConfiguration(processKey, updateDto);
    verify(digestService).handleProcessUpdate(processKey, updateDto);
  }

  @Test
  void shouldThrowExceptionWhenUnauthorizedForProcessUpdate() {
    // given
    final String userId = "user123";
    final String processKey = "processKey1";

    final ProcessUpdateDto updateDto =
        new ProcessUpdateDto("owner456", new ProcessDigestRequestDto(true));

    final List<String> tenants = new ArrayList<>();
    tenants.add("1");

    when(definitionService.getProcessDefinitionWithTenants(processKey))
        .thenReturn(
            Optional.of(
                new DefinitionWithTenantIdsDto(
                    processKey, "Process Name", PROCESS, tenants, Set.of())));
    when(definitionAuthorizationService.isAuthorizedToAccessDefinition(
            userId, PROCESS, processKey, List.of("tenant1")))
        .thenReturn(false);

    // when
    final Throwable thrown =
        catchThrowable(() -> processOverviewService.updateProcess(userId, processKey, updateDto));

    // then
    assertThat(thrown).isInstanceOf(ForbiddenException.class);
  }

  @Test
  void shouldUpdateOwnerIfDefinitionIsImported() {
    // given
    final String userId = "user123";
    final String processKey = "processKey1";
    final String ownerId = "owner456";

    when(definitionService.getLatestVersionToKey(PROCESS, processKey)).thenReturn("1");

    when(identityService.getUserById(ownerId)).thenReturn(Optional.of(new UserDto(ownerId)));
    when(identityService.isUserAuthorizedToAccessIdentity(
            userId, new IdentityDto(ownerId, IdentityType.USER)))
        .thenReturn(true);

    final List<String> tenants = new ArrayList<>();
    tenants.add("1");
    when(definitionService.getProcessDefinitionWithTenants(processKey))
        .thenReturn(Optional.of(new DefinitionWithTenantIdsDto(tenants)));

    when(definitionAuthorizationService.isAuthorizedToAccessDefinition(any(), any(), any(), any()))
        .thenReturn(true);

    // when
    processOverviewService.updateProcessOwnerIfNotSet(userId, processKey, ownerId);

    // then
    verify(processOverviewWriter).updateProcessOwnerIfNotSet(processKey, ownerId);
  }
}
