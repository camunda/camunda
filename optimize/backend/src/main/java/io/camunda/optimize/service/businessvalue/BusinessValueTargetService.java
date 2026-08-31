/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.businessvalue;

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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Application-layer entry point for the two BVD target REST endpoints. Delegates persistence to
 * {@link BusinessValueTargetWriter} / {@link BusinessValueTargetRepository}.
 *
 * <p>A target write refreshes the definition's overview rows synchronously, via {@link
 * BusinessValueOverviewComputeService#refreshTargetOnRows}, so the caller's next {@code GET
 * /business-value/overview} shows the new verdict. Without it the row keeps the target it was last
 * swept with, and the stale-read backstop does not help: it fires on the age of the measurement,
 * and a row measured an hour ago carrying an hour-old target is not stale by that measure. No
 * report is evaluated on this path — the verdict is re-derived from the values already on the row,
 * which is what keeps it cheap enough to run inside the request.
 *
 * <p>Field-level input validation ({@code automationRateTargetPct ∈ [0, 100]}, {@code
 * cycleTimeTarget.value ≥ 0}) is expressed as JSR-380 annotations on {@link
 * BusinessValueTargetUpsertRequestDto} / {@link CycleTimeTargetDto} and enforced by the
 * {@code @Valid} on the controller; the service only holds constraints Bean Validation cannot:
 * cross-field ("both value+unit set, or both null"), enum subset (only {@code MILLIS..DAYS}), and
 * arithmetic overflow on unit → millis conversion.
 */
@Component
public class BusinessValueTargetService {

  private static final Map<TargetValueUnit, Duration> UNIT_DURATIONS =
      BusinessValueTargetWriter.SUPPORTED_CYCLE_TIME_UNIT_DURATIONS;

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(BusinessValueTargetService.class);

  private final BusinessValueTargetWriter writer;
  private final BusinessValueTargetRepository repository;
  private final TenantService tenantService;
  private final DefinitionService definitionService;
  private final BusinessValueOverviewComputeService overviewComputeService;

  public BusinessValueTargetService(
      final BusinessValueTargetWriter writer,
      final BusinessValueTargetRepository repository,
      final TenantService tenantService,
      final DefinitionService definitionService,
      final BusinessValueOverviewComputeService overviewComputeService) {
    this.writer = writer;
    this.repository = repository;
    this.tenantService = tenantService;
    this.definitionService = definitionService;
    this.overviewComputeService = overviewComputeService;
  }

  public BusinessValueTargetResponseDto readTarget(
      final String userId, final String tenantId, final String processDefinitionKey) {
    ensureTenantAccess(userId, tenantId);
    final Optional<BusinessValueTargetDto> row =
        repository.getByKey(tenantId, processDefinitionKey);
    return row.map(BusinessValueTargetService::toResponseDto)
        .orElseGet(() -> emptyResponse(tenantId, processDefinitionKey));
  }

  public BusinessValueTargetResponseDto upsertTarget(
      final String userId,
      final String tenantId,
      final String processDefinitionKey,
      final BusinessValueTargetUpsertRequestDto request) {
    ensureTenantAccess(userId, tenantId);
    ensureDefinitionExistsForTenant(tenantId, processDefinitionKey);

    final BusinessValueTargetDto toWrite =
        toPersistenceDto(tenantId, processDefinitionKey, request, userId);
    writer.upsertTarget(toWrite);
    refreshOverviewRows(toWrite);
    return toResponseDto(toWrite);
  }

  /**
   * Propagates the saved target onto the overview rows, after the target itself is durable.
   *
   * <p>A failure here is logged rather than surfaced: the target is already persisted, and the next
   * sweep re-derives the rows from it, so the only cost is that L0 lags until then. Failing the
   * request instead would leave the caller unable to tell whether their target was saved — the
   * worse of the two outcomes, and the reason this runs after the write rather than as part of it.
   */
  private void refreshOverviewRows(final BusinessValueTargetDto target) {
    try {
      overviewComputeService.refreshTargetOnRows(target);
    } catch (final Exception e) {
      LOG.warn(
          "Business-value target for tenant [{}] definition [{}] was saved, but refreshing its "
              + "overview rows failed; the overview will catch up on the next sweep.",
          target.getTenantId(),
          target.getProcessDefinitionKey(),
          e);
    }
  }

  private void ensureTenantAccess(final String userId, final String tenantId) {
    if (!tenantService.isAuthorizedToSeeTenant(userId, tenantId)) {
      throw new ForbiddenException(
          "user is not authorized to access business-value targets for the requested tenant");
    }
  }

  private void ensureDefinitionExistsForTenant(
      final String tenantId, final String processDefinitionKey) {
    final boolean exists =
        definitionService
            .getProcessDefinitionWithTenants(processDefinitionKey)
            .map(DefinitionWithTenantIdsDto::getTenantIds)
            .map(tenants -> tenants.contains(tenantId))
            .orElse(false);
    if (!exists) {
      throw new NotFoundException(
          "no process definition found for the given (tenantId, processDefinitionKey) pair");
    }
  }

  private BusinessValueTargetDto toPersistenceDto(
      final String tenantId,
      final String processDefinitionKey,
      final BusinessValueTargetUpsertRequestDto request,
      final String userId) {
    final Long cycleTimeMillis;
    final TargetValueUnit cycleTimeUnit;
    final CycleTimeTargetDto cycleTimeInput = request.cycleTimeTarget();
    if (isCleared(cycleTimeInput)) {
      cycleTimeMillis = null;
      cycleTimeUnit = null;
    } else {
      validateCycleTimeSemantics(cycleTimeInput);
      cycleTimeMillis = toMillis(cycleTimeInput.value(), cycleTimeInput.unit());
      cycleTimeUnit = cycleTimeInput.unit();
    }
    return new BusinessValueTargetDto(
        processDefinitionKey,
        tenantId,
        cycleTimeMillis,
        cycleTimeUnit,
        request.automationRateTargetPct(),
        OffsetDateTime.now(ZoneOffset.UTC),
        userId);
  }

  private static boolean isCleared(final CycleTimeTargetDto input) {
    return input == null || (input.value() == null && input.unit() == null);
  }

  private static void validateCycleTimeSemantics(final CycleTimeTargetDto input) {
    if ((input.value() == null) != (input.unit() == null)) {
      throw new BadRequestException(
          "cycleTimeTarget.value and cycleTimeTarget.unit must both be set or both be null");
    }
    if (!UNIT_DURATIONS.containsKey(input.unit())) {
      throw new BadRequestException(
          "cycleTimeTarget.unit ["
              + input.unit()
              + "] is not supported; allowed: "
              + UNIT_DURATIONS.keySet());
    }
  }

  private static long toMillis(final Long value, final TargetValueUnit unit) {
    try {
      return UNIT_DURATIONS.get(unit).multipliedBy(value).toMillis();
    } catch (final ArithmeticException e) {
      throw new BadRequestException(
          "cycleTimeTarget.value ["
              + value
              + " "
              + unit
              + "] is too large; overflowed when converting to milliseconds",
          e);
    }
  }

  private static BusinessValueTargetResponseDto toResponseDto(final BusinessValueTargetDto row) {
    final CycleTimeTargetDto cycleTime =
        row.getCycleTimeTargetMillis() == null
            ? null
            : new CycleTimeTargetDto(
                fromMillis(row.getCycleTimeTargetMillis(), row.getCycleTimeTargetUnit()),
                row.getCycleTimeTargetUnit(),
                row.getCycleTimeTargetMillis());
    return new BusinessValueTargetResponseDto(
        row.getTenantId(),
        row.getProcessDefinitionKey(),
        cycleTime,
        row.getAutomationRateTargetPct(),
        row.getUpdatedAt(),
        row.getUpdatedBy());
  }

  private static long fromMillis(final long millis, final TargetValueUnit unit) {
    return millis / UNIT_DURATIONS.get(unit).toMillis();
  }

  private static BusinessValueTargetResponseDto emptyResponse(
      final String tenantId, final String processDefinitionKey) {
    return new BusinessValueTargetResponseDto(
        tenantId, processDefinitionKey, null, null, null, null);
  }
}
