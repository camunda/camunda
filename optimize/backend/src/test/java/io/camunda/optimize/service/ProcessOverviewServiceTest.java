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
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;
import io.camunda.optimize.rest.exceptions.BadRequestException;
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.service.db.reader.ProcessOverviewReader;
import io.camunda.optimize.service.db.writer.ProcessOverviewWriter;
import io.camunda.optimize.service.digest.DigestService;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ProcessOverviewServiceTest {

  private static final String EN_LOCALE = "en";
  private static final String USER_ID = "user123";
  private static final String PROCESS_KEY = "processKey1";
  private static final String OWNER_ID = "owner123";
  private static final String OWNER_NAME = "Owner Name";
  private static final String PROCESS_NAME = "Process Name";
  private static final List<String> TENANT_LIST = getSingleTenantList();
  public static final DefinitionWithTenantIdsDto DEFINITION_DTO =
      new DefinitionWithTenantIdsDto(PROCESS_KEY, PROCESS_NAME, PROCESS, TENANT_LIST, Set.of());

  private final DefinitionService definitionService = mock(DefinitionService.class);
  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService =
      mock(DataSourceDefinitionAuthorizationService.class);
  private final ProcessOverviewWriter processOverviewWriter = mock(ProcessOverviewWriter.class);
  private final ProcessOverviewReader processOverviewReader = mock(ProcessOverviewReader.class);
  private final AbstractIdentityService identityService = mock(AbstractIdentityService.class);
  private final KpiService kpiService = mock(KpiService.class);
  private final DigestService digestService = mock(DigestService.class);

  private final ProcessOverviewService underTest =
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
    when(definitionService.getAllDefinitionsWithTenants(PROCESS))
        .thenReturn(List.of(DEFINITION_DTO));
    when(definitionService.getCachedTenantToLatestDefinitionMap(PROCESS, PROCESS_KEY))
        .thenReturn(Map.of(PROCESS_KEY, new ProcessDefinitionOptimizeDto()));
    when(definitionAuthorizationService.isAuthorizedToAccessDefinition(
            USER_ID, PROCESS, PROCESS_KEY, DEFINITION_DTO.getTenantIds()))
        .thenReturn(true);

    final Map<String, ProcessOverviewDto> overviewMap =
        Map.of(
            PROCESS_KEY,
            new ProcessOverviewDto(
                OWNER_ID, PROCESS_KEY, new ProcessDigestDto(false, Map.of()), Map.of()));
    when(processOverviewReader.getProcessOverviewsByKey(Set.of(PROCESS_KEY)))
        .thenReturn(overviewMap);

    when(identityService.getIdentityNameById(OWNER_ID)).thenReturn(Optional.of(OWNER_NAME));

    // when
    final List<ProcessOverviewResponseDto> result =
        underTest.getAllProcessOverviews(USER_ID, EN_LOCALE);

    // then
    assertThat(result)
        .singleElement()
        .satisfies(
            response -> {
              assertThat(response.getProcessDefinitionKey()).isEqualTo(PROCESS_KEY);
              assertThat(response.getProcessDefinitionName()).isEqualTo(PROCESS_KEY);
              assertThat(response.getOwner().getId()).isEqualTo(OWNER_ID);
              assertThat(response.getOwner().getName()).isEqualTo(OWNER_NAME);
            });
  }

  @Test
  void shouldReturnProcessOverviewsWithoutOwnerWhenUserIsAuthorized() {
    // given
    when(definitionService.getAllDefinitionsWithTenants(PROCESS))
        .thenReturn(List.of(DEFINITION_DTO));

    when(definitionService.getCachedTenantToLatestDefinitionMap(PROCESS, PROCESS_KEY))
        .thenReturn(Map.of(PROCESS_KEY, new ProcessDefinitionOptimizeDto()));
    when(definitionAuthorizationService.isAuthorizedToAccessDefinition(
            USER_ID, PROCESS, PROCESS_KEY, DEFINITION_DTO.getTenantIds()))
        .thenReturn(true);

    when(processOverviewReader.getProcessOverviewsByKey(Set.of(PROCESS_KEY))).thenReturn(Map.of());

    when(identityService.getIdentityNameById(OWNER_ID)).thenReturn(Optional.of(OWNER_NAME));

    // when
    final List<ProcessOverviewResponseDto> result =
        underTest.getAllProcessOverviews(USER_ID, EN_LOCALE);

    // then
    assertThat(result)
        .singleElement()
        .satisfies(
            response -> {
              assertThat(response.getProcessDefinitionKey()).isEqualTo(PROCESS_KEY);
              assertThat(response.getProcessDefinitionName()).isEqualTo(PROCESS_KEY);
              assertThat(response.getOwner().getId()).isNull();
              assertThat(response.getOwner().getName()).isNull();
            });
  }

  @Test
  void shouldReturnEmptyListProcessOverviewsWhenDefinitionsEmpty() {
    // given
    when(definitionService.getAllDefinitionsWithTenants(PROCESS)).thenReturn(List.of());

    // when
    final List<ProcessOverviewResponseDto> result =
        underTest.getAllProcessOverviews(USER_ID, EN_LOCALE);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldUpdateProcessWhenAuthorizedAndValid() {
    // given
    final ProcessUpdateDto updateDto =
        new ProcessUpdateDto(OWNER_ID, new ProcessDigestRequestDto(true));

    when(definitionService.getProcessDefinitionWithTenants(PROCESS_KEY))
        .thenReturn(Optional.of(DEFINITION_DTO));
    when(definitionAuthorizationService.isAuthorizedToAccessDefinition(any(), any(), any(), any()))
        .thenReturn(true);
    when(identityService.getUserById(OWNER_ID)).thenReturn(Optional.of(new UserDto(OWNER_ID)));
    when(identityService.isUserAuthorizedToAccessIdentity(
            USER_ID, new IdentityDto(OWNER_ID, IdentityType.USER)))
        .thenReturn(true);

    // when
    underTest.updateProcess(USER_ID, PROCESS_KEY, updateDto);

    // then
    verify(processOverviewWriter).updateProcessConfiguration(PROCESS_KEY, updateDto);
    verify(digestService).handleProcessUpdate(PROCESS_KEY, updateDto);
  }

  @Test
  void shouldThrowExceptionWhenUnauthorizedForProcessUpdate() {
    // given
    final ProcessUpdateDto updateDto =
        new ProcessUpdateDto(OWNER_ID, new ProcessDigestRequestDto(true));

    when(definitionService.getProcessDefinitionWithTenants(PROCESS_KEY))
        .thenReturn(Optional.of(DEFINITION_DTO));
    when(definitionAuthorizationService.isAuthorizedToAccessDefinition(
            USER_ID, PROCESS, PROCESS_KEY, getSingleTenantList()))
        .thenReturn(false);

    // when
    final Throwable thrown =
        catchThrowable(() -> underTest.updateProcess(USER_ID, PROCESS_KEY, updateDto));

    // then
    assertThat(thrown)
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining(
            "User is not authorized to access the process definition with key processKey1");
  }

  @Test
  void shouldThrowExceptionWhenUserAuthorizationFails() {
    // given
    final ProcessUpdateDto updateDto =
        new ProcessUpdateDto(OWNER_ID, new ProcessDigestRequestDto(true));

    when(definitionService.getProcessDefinitionWithTenants(PROCESS_KEY))
        .thenReturn(Optional.of(DEFINITION_DTO));
    when(definitionAuthorizationService.isAuthorizedToAccessDefinition(
            USER_ID, PROCESS, PROCESS_KEY, TENANT_LIST))
        .thenReturn(true);
    when(identityService.isUserAuthorizedToAccessIdentity(
            USER_ID, new IdentityDto(USER_ID, IdentityType.GROUP)))
        .thenReturn(false);

    // when
    final Throwable thrown =
        catchThrowable(() -> underTest.updateProcess(USER_ID, PROCESS_KEY, updateDto));

    // then
    assertThat(thrown)
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining(
            "Could not find a user with ID owner123 that the user user123 is authorized to see.");
  }

  @Test
  void shouldThrowExceptionWhenOwnerIdIsNullAndDigestEnabled() {
    // given
    final ProcessUpdateDto updateDto =
        new ProcessUpdateDto(null, new ProcessDigestRequestDto(true));

    when(definitionService.getProcessDefinitionWithTenants(PROCESS_KEY))
        .thenReturn(Optional.of(DEFINITION_DTO));
    when(definitionAuthorizationService.isAuthorizedToAccessDefinition(
            USER_ID, PROCESS, PROCESS_KEY, TENANT_LIST))
        .thenReturn(true);
    when(identityService.isUserAuthorizedToAccessIdentity(
            USER_ID, new IdentityDto(USER_ID, IdentityType.GROUP)))
        .thenReturn(false);

    // when
    final Throwable thrown =
        catchThrowable(() -> underTest.updateProcess(USER_ID, PROCESS_KEY, updateDto));

    // then
    assertThat(thrown)
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Process digest cannot be enabled if no owner is set");
  }

  @Test
  void shouldSuccessfullyUpdateProcess() {
    // given
    final ProcessUpdateDto updateDto =
        new ProcessUpdateDto(OWNER_ID, new ProcessDigestRequestDto(true));

    when(definitionService.getProcessDefinitionWithTenants(PROCESS_KEY))
        .thenReturn(
            Optional.of(
                new DefinitionWithTenantIdsDto(
                    PROCESS_KEY, PROCESS_NAME, PROCESS, TENANT_LIST, Set.of())));
    when(definitionAuthorizationService.isAuthorizedToAccessDefinition(
            USER_ID, PROCESS, PROCESS_KEY, TENANT_LIST))
        .thenReturn(true);

    when(identityService.getUserById(any())).thenReturn(Optional.of(new UserDto(USER_ID)));

    // when/then
    underTest.updateProcess(USER_ID, PROCESS_KEY, updateDto);
  }

  @Test
  void shouldUpdateOwnerIfDefinitionIsImported() {
    // given
    when(definitionService.getLatestVersionToKey(PROCESS, PROCESS_KEY)).thenReturn("1");

    when(identityService.getUserById(OWNER_ID)).thenReturn(Optional.of(new UserDto(OWNER_ID)));
    when(identityService.isUserAuthorizedToAccessIdentity(
            USER_ID, new IdentityDto(OWNER_ID, IdentityType.USER)))
        .thenReturn(true);

    when(definitionService.getProcessDefinitionWithTenants(PROCESS_KEY))
        .thenReturn(Optional.of(new DefinitionWithTenantIdsDto(TENANT_LIST)));

    when(definitionAuthorizationService.isAuthorizedToAccessDefinition(any(), any(), any(), any()))
        .thenReturn(true);

    // when
    underTest.updateProcessOwnerIfNotSet(USER_ID, PROCESS_KEY, OWNER_ID);

    // then
    verify(processOverviewWriter).updateProcessOwnerIfNotSet(PROCESS_KEY, OWNER_ID);
  }

  @Test
  void shouldThrowExceptionWhenOwnerIdIsNull() {
    // when
    final Throwable thrown =
        catchThrowable(() -> underTest.updateProcessOwnerIfNotSet(USER_ID, PROCESS_KEY, null));

    // then
    assertThat(thrown)
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Owner ID cannot be empty!");
  }

  private static List<String> getSingleTenantList() {
    final List<String> tenantList = new ArrayList<>();
    tenantList.add("tenant1");
    return tenantList;
  }
}
