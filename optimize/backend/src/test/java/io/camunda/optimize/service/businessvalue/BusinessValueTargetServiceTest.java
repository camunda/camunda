/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.businessvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetResponseDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetUpsertRequestDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.CycleTimeTargetDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import io.camunda.optimize.rest.exceptions.BadRequestException;
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.repository.BusinessValueTargetRepository;
import io.camunda.optimize.service.db.writer.BusinessValueTargetWriter;
import io.camunda.optimize.service.tenant.TenantService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class BusinessValueTargetServiceTest {

  private static final String USER = "user-1";
  private static final String TENANT = "tenant-a";
  private static final String PROCESS_KEY = "invoice-automation";

  private BusinessValueTargetWriter writer;
  private BusinessValueTargetRepository repository;
  private TenantService tenantService;
  private DefinitionService definitionService;
  private BusinessValueOverviewComputeService overviewComputeService;
  private BusinessValueTargetService service;

  @BeforeEach
  void setUp() {
    writer = mock(BusinessValueTargetWriter.class);
    repository = mock(BusinessValueTargetRepository.class);
    tenantService = mock(TenantService.class);
    definitionService = mock(DefinitionService.class);
    overviewComputeService = mock(BusinessValueOverviewComputeService.class);
    when(tenantService.isAuthorizedToSeeTenant(anyString(), anyString())).thenReturn(true);
    stubDefinitionExists(PROCESS_KEY, TENANT);
    service =
        new BusinessValueTargetService(
            writer, repository, tenantService, definitionService, overviewComputeService);
  }

  private void stubDefinitionExists(final String processDefinitionKey, final String... tenantIds) {
    final DefinitionWithTenantIdsDto def = mock(DefinitionWithTenantIdsDto.class);
    when(def.getTenantIds()).thenReturn(List.of(tenantIds));
    when(definitionService.getProcessDefinitionWithTenants(processDefinitionKey))
        .thenReturn(Optional.of(def));
  }

  // --- read: happy path + empty state ---

  @Test
  void shouldReturnEmptyResponseWhenNoTargetExists() {
    // given
    when(repository.getByKey(TENANT, PROCESS_KEY)).thenReturn(Optional.empty());

    // when
    final BusinessValueTargetResponseDto response = service.readTarget(USER, TENANT, PROCESS_KEY);

    // then
    assertThat(response.tenantId()).isEqualTo(TENANT);
    assertThat(response.processKey()).isEqualTo(PROCESS_KEY);
    assertThat(response.cycleTimeTarget()).isNull();
    assertThat(response.automationRateTargetPct()).isNull();
    assertThat(response.updatedAt()).isNull();
    assertThat(response.updatedBy()).isNull();
  }

  @Test
  void shouldMapPersistedTargetIntoResponseWithComputedMillis() {
    // given
    final OffsetDateTime updated = OffsetDateTime.parse("2026-08-01T00:00:00Z");
    when(repository.getByKey(TENANT, PROCESS_KEY))
        .thenReturn(
            Optional.of(
                new BusinessValueTargetDto(
                    PROCESS_KEY, TENANT, 28_800_000L, TargetValueUnit.HOURS, 85, updated, "u")));

    // when
    final BusinessValueTargetResponseDto response = service.readTarget(USER, TENANT, PROCESS_KEY);

    // then
    assertThat(response.cycleTimeTarget())
        .isEqualTo(new CycleTimeTargetDto(8L, TargetValueUnit.HOURS, 28_800_000L));
    assertThat(response.automationRateTargetPct()).isEqualTo(85);
    assertThat(response.updatedAt()).isEqualTo(updated);
    assertThat(response.updatedBy()).isEqualTo("u");
  }

  // --- upsert: happy path ---

  @Test
  void shouldPersistUpsertAndReturnResponse() {
    // given
    final BusinessValueTargetUpsertRequestDto request =
        new BusinessValueTargetUpsertRequestDto(
            new CycleTimeTargetDto(2L, TargetValueUnit.DAYS, null), 90);

    // when
    final BusinessValueTargetResponseDto response =
        service.upsertTarget(USER, TENANT, PROCESS_KEY, request);

    // then — writer received the flat persistence DTO with converted millis
    final ArgumentCaptor<BusinessValueTargetDto> writeCaptor =
        ArgumentCaptor.forClass(BusinessValueTargetDto.class);
    verify(writer).upsertTarget(writeCaptor.capture());
    final BusinessValueTargetDto written = writeCaptor.getValue();
    assertThat(written.getTenantId()).isEqualTo(TENANT);
    assertThat(written.getProcessDefinitionKey()).isEqualTo(PROCESS_KEY);
    assertThat(written.getCycleTimeTargetMillis()).isEqualTo(172_800_000L);
    assertThat(written.getCycleTimeTargetUnit()).isEqualTo(TargetValueUnit.DAYS);
    assertThat(written.getAutomationRateTargetPct()).isEqualTo(90);
    assertThat(written.getUpdatedBy()).isEqualTo(USER);
    assertThat(written.getUpdatedAt()).isNotNull();

    // and — response echoes the written state
    assertThat(response.cycleTimeTarget().millis()).isEqualTo(172_800_000L);
    assertThat(response.automationRateTargetPct()).isEqualTo(90);
  }

  // --- upsert: overview coherence ---

  /**
   * Without this the saved target does not reach L0 until the next sweep, because the overview row
   * carries the target it was last computed with and the stale-read backstop keys off the age of
   * the measurement, not of the target — a freshly measured row holding a stale target looks
   * perfectly fresh to it.
   */
  @Test
  void shouldRefreshOverviewRowsWithTheTargetItJustWrote() {
    // given
    final BusinessValueTargetUpsertRequestDto request =
        new BusinessValueTargetUpsertRequestDto(
            new CycleTimeTargetDto(2L, TargetValueUnit.DAYS, null), 90);

    // when
    service.upsertTarget(USER, TENANT, PROCESS_KEY, request);

    // then the refresh runs after the target is durable, and on exactly what was persisted
    final ArgumentCaptor<BusinessValueTargetDto> refreshCaptor =
        ArgumentCaptor.forClass(BusinessValueTargetDto.class);
    final InOrder inOrder = inOrder(writer, overviewComputeService);
    inOrder.verify(writer).upsertTarget(any(BusinessValueTargetDto.class));
    inOrder.verify(overviewComputeService).refreshTargetOnRows(refreshCaptor.capture());
    final BusinessValueTargetDto refreshed = refreshCaptor.getValue();
    assertThat(refreshed.getTenantId()).isEqualTo(TENANT);
    assertThat(refreshed.getProcessDefinitionKey()).isEqualTo(PROCESS_KEY);
    assertThat(refreshed.getCycleTimeTargetMillis()).isEqualTo(172_800_000L);
    assertThat(refreshed.getAutomationRateTargetPct()).isEqualTo(90);
  }

  /**
   * The target is already durable by the time the refresh runs, and the next sweep re-derives the
   * rows from it. Failing the request instead would leave the caller unable to tell whether their
   * target saved — worse than an overview that lags one interval.
   */
  @Test
  void shouldStillReturnTheSavedTargetWhenRefreshingTheOverviewFails() {
    // given a refresh that blows up
    doThrow(new IllegalStateException("elasticsearch is having a moment"))
        .when(overviewComputeService)
        .refreshTargetOnRows(any());
    final BusinessValueTargetUpsertRequestDto request =
        new BusinessValueTargetUpsertRequestDto(
            new CycleTimeTargetDto(2L, TargetValueUnit.DAYS, null), 90);

    // when
    final BusinessValueTargetResponseDto response =
        service.upsertTarget(USER, TENANT, PROCESS_KEY, request);

    // then the save stands and the caller sees what was persisted
    verify(writer).upsertTarget(any(BusinessValueTargetDto.class));
    assertThat(response.cycleTimeTarget().millis()).isEqualTo(172_800_000L);
    assertThat(response.automationRateTargetPct()).isEqualTo(90);
  }

  /** A rejected upsert must not touch the overview — there is no new target to propagate. */
  @Test
  void shouldNotRefreshOverviewRowsWhenTheUpsertIsRejected() {
    // given a request that fails the service's own cross-field validation
    final BusinessValueTargetUpsertRequestDto missingUnit =
        new BusinessValueTargetUpsertRequestDto(new CycleTimeTargetDto(2L, null, null), 90);

    // when / then
    assertThatThrownBy(() -> service.upsertTarget(USER, TENANT, PROCESS_KEY, missingUnit))
        .isInstanceOf(BadRequestException.class);
    verify(overviewComputeService, never()).refreshTargetOnRows(any());
  }

  // --- upsert: clear semantics ---

  @Test
  void shouldTreatAllNullNestedCycleTimeAsClear() {
    // given — {value:null, unit:null} previously slipped past the both-or-neither check and NPE'd
    // in toMillis. Must be treated as an equivalent clear operation.
    final BusinessValueTargetUpsertRequestDto request =
        new BusinessValueTargetUpsertRequestDto(new CycleTimeTargetDto(null, null, null), 85);

    // when
    service.upsertTarget(USER, TENANT, PROCESS_KEY, request);

    // then
    final ArgumentCaptor<BusinessValueTargetDto> writeCaptor =
        ArgumentCaptor.forClass(BusinessValueTargetDto.class);
    verify(writer).upsertTarget(writeCaptor.capture());
    assertThat(writeCaptor.getValue().getCycleTimeTargetMillis()).isNull();
    assertThat(writeCaptor.getValue().getCycleTimeTargetUnit()).isNull();
    assertThat(writeCaptor.getValue().getAutomationRateTargetPct()).isEqualTo(85);
  }

  @Test
  void shouldAllowClearingBothTargetsWithNullBody() {
    // given
    final BusinessValueTargetUpsertRequestDto clear =
        new BusinessValueTargetUpsertRequestDto(null, null);

    // when
    service.upsertTarget(USER, TENANT, PROCESS_KEY, clear);

    // then
    final ArgumentCaptor<BusinessValueTargetDto> writeCaptor =
        ArgumentCaptor.forClass(BusinessValueTargetDto.class);
    verify(writer).upsertTarget(writeCaptor.capture());
    assertThat(writeCaptor.getValue().getCycleTimeTargetMillis()).isNull();
    assertThat(writeCaptor.getValue().getCycleTimeTargetUnit()).isNull();
    assertThat(writeCaptor.getValue().getAutomationRateTargetPct()).isNull();
  }

  // --- upsert: semantic validation the service owns (Bean Validation handles range/positive) ---

  @Test
  void shouldRejectMixedValueAndUnitOnCycleTime() {
    // given
    final BusinessValueTargetUpsertRequestDto missingUnit =
        new BusinessValueTargetUpsertRequestDto(new CycleTimeTargetDto(8L, null, null), null);

    // when + then
    assertThatThrownBy(() -> service.upsertTarget(USER, TENANT, PROCESS_KEY, missingUnit))
        .isInstanceOf(BadRequestException.class);
    verifyNoInteractions(writer);
  }

  @Test
  void shouldRejectUnsupportedCycleTimeUnit() {
    // given — WEEKS is a valid TargetValueUnit but outside the supported subset
    final BusinessValueTargetUpsertRequestDto weeks =
        new BusinessValueTargetUpsertRequestDto(
            new CycleTimeTargetDto(1L, TargetValueUnit.WEEKS, null), null);

    // when + then
    assertThatThrownBy(() -> service.upsertTarget(USER, TENANT, PROCESS_KEY, weeks))
        .isInstanceOf(BadRequestException.class);
    verifyNoInteractions(writer);
  }

  @Test
  void shouldRejectOverflowingCycleTimeConversion() {
    // given
    final BusinessValueTargetUpsertRequestDto huge =
        new BusinessValueTargetUpsertRequestDto(
            new CycleTimeTargetDto(Long.MAX_VALUE, TargetValueUnit.DAYS, null), null);

    // when + then
    assertThatThrownBy(() -> service.upsertTarget(USER, TENANT, PROCESS_KEY, huge))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("overflow");
    verifyNoInteractions(writer);
  }

  // --- upsert: definition existence guard ---

  @Test
  void shouldReturnNotFoundWhenDefinitionMissing() {
    // given
    when(definitionService.getProcessDefinitionWithTenants("ghost")).thenReturn(Optional.empty());

    // when + then
    assertThatThrownBy(
            () ->
                service.upsertTarget(
                    USER, TENANT, "ghost", new BusinessValueTargetUpsertRequestDto(null, 50)))
        .isInstanceOf(NotFoundException.class);
    verifyNoInteractions(writer);
  }

  @Test
  void shouldReturnNotFoundWhenDefinitionExistsButNotForCallerTenant() {
    // given — definition exists but only under a different tenant
    stubDefinitionExists(PROCESS_KEY, "other-tenant");

    // when + then
    assertThatThrownBy(
            () ->
                service.upsertTarget(
                    USER, TENANT, PROCESS_KEY, new BusinessValueTargetUpsertRequestDto(null, 50)))
        .isInstanceOf(NotFoundException.class);
    verifyNoInteractions(writer);
  }

  // --- upsert: tenant-authorization guard ---

  @Test
  void shouldForbidReadWhenTenantNotAuthorized() {
    // given
    when(tenantService.isAuthorizedToSeeTenant(USER, TENANT)).thenReturn(false);

    // when + then
    assertThatThrownBy(() -> service.readTarget(USER, TENANT, PROCESS_KEY))
        .isInstanceOf(ForbiddenException.class);
    verify(repository, never()).getByKey(anyString(), anyString());
  }

  @Test
  void shouldForbidUpsertWhenTenantNotAuthorized() {
    // given
    when(tenantService.isAuthorizedToSeeTenant(USER, TENANT)).thenReturn(false);

    // when + then
    assertThatThrownBy(
            () ->
                service.upsertTarget(
                    USER, TENANT, PROCESS_KEY, new BusinessValueTargetUpsertRequestDto(null, 50)))
        .isInstanceOf(ForbiddenException.class);
    verifyNoInteractions(writer);
    verify(definitionService, never()).getProcessDefinitionWithTenants(any());
  }

  // --- upsert: special-character tenant IDs and process definition keys reach the service intact.
  // Beans below prove the service layer is transparent to the string content (the URL-decoding
  // step lives in the controller / servlet stack, exercised by the REST-layer test).

  @ParameterizedTest(name = "shouldUpsertForSpecialTenantId[{0}]")
  @ValueSource(
      strings = {
        "<default>",
        "tenant-with-hyphens",
        "tenant_with_underscores",
        "tenant.with.dots",
        "Mixed-Case_Tenant.01"
      })
  void shouldUpsertForSpecialTenantId(final String specialTenant) {
    // given
    stubDefinitionExists(PROCESS_KEY, specialTenant);
    final BusinessValueTargetUpsertRequestDto request =
        new BusinessValueTargetUpsertRequestDto(
            new CycleTimeTargetDto(1L, TargetValueUnit.HOURS, null), 50);

    // when
    service.upsertTarget(USER, specialTenant, PROCESS_KEY, request);

    // then
    final ArgumentCaptor<BusinessValueTargetDto> writeCaptor =
        ArgumentCaptor.forClass(BusinessValueTargetDto.class);
    verify(writer).upsertTarget(writeCaptor.capture());
    assertThat(writeCaptor.getValue().getTenantId()).isEqualTo(specialTenant);
  }

  @ParameterizedTest(name = "shouldUpsertForSpecialProcessDefinitionKey[{0}]")
  @ValueSource(
      strings = {
        "invoice-automation",
        "invoice_automation",
        "com.acme.invoice",
        "Order-01",
      })
  void shouldUpsertForSpecialProcessDefinitionKey(final String specialKey) {
    // given
    stubDefinitionExists(specialKey, TENANT);
    final BusinessValueTargetUpsertRequestDto request =
        new BusinessValueTargetUpsertRequestDto(null, 25);

    // when
    service.upsertTarget(USER, TENANT, specialKey, request);

    // then
    final ArgumentCaptor<BusinessValueTargetDto> writeCaptor =
        ArgumentCaptor.forClass(BusinessValueTargetDto.class);
    verify(writer).upsertTarget(writeCaptor.capture());
    assertThat(writeCaptor.getValue().getProcessDefinitionKey()).isEqualTo(specialKey);
  }
}
