/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import io.camunda.optimize.service.db.repository.BusinessValueTargetRepository;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class BusinessValueTargetWriter {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(BusinessValueTargetWriter.class);

  private static final Set<TargetValueUnit> SUPPORTED_CYCLE_TIME_UNITS =
      EnumSet.of(
          TargetValueUnit.MILLIS,
          TargetValueUnit.SECONDS,
          TargetValueUnit.MINUTES,
          TargetValueUnit.HOURS,
          TargetValueUnit.DAYS);

  private final BusinessValueTargetRepository repository;

  public BusinessValueTargetWriter(final BusinessValueTargetRepository repository) {
    this.repository = repository;
  }

  public void upsertTarget(final BusinessValueTargetDto target) {
    validate(target);
    LOG.debug(
        "Upserting business-value target for definition [{}] tenant [{}]",
        target.getProcessDefinitionKey(),
        target.getTenantId());
    repository.upsert(target);
  }

  private void validate(final BusinessValueTargetDto target) {
    if (target == null) {
      throw new IllegalArgumentException("target must not be null");
    }
    if (target.getProcessDefinitionKey() == null || target.getProcessDefinitionKey().isBlank()) {
      throw new IllegalArgumentException("processDefinitionKey must not be null or blank");
    }
    if (target.getTenantId() == null || target.getTenantId().isBlank()) {
      throw new IllegalArgumentException(
          "tenantId must not be null or blank on a business-value target");
    }
    final Long cycleTimeMillis = target.getCycleTimeTargetMillis();
    final TargetValueUnit unit = target.getCycleTimeTargetUnit();
    if ((cycleTimeMillis == null) != (unit == null)) {
      throw new IllegalArgumentException(
          "cycleTimeTargetMillis and cycleTimeTargetUnit must both be null or both be set, "
              + "but got millis=["
              + cycleTimeMillis
              + "] unit=["
              + unit
              + "]");
    }
    if (cycleTimeMillis != null && cycleTimeMillis < 0) {
      throw new IllegalArgumentException(
          "cycleTimeTargetMillis must be non-negative but was " + cycleTimeMillis);
    }
    if (unit != null && !SUPPORTED_CYCLE_TIME_UNITS.contains(unit)) {
      throw new IllegalArgumentException(
          "cycleTimeTargetUnit ["
              + unit
              + "] is not supported by business-value targets; allowed: "
              + SUPPORTED_CYCLE_TIME_UNITS);
    }
    final Integer pct = target.getAutomationRateTargetPct();
    if (pct != null && (pct < 0 || pct > 100)) {
      throw new IllegalArgumentException(
          "automationRateTargetPct must be within [0, 100] but was " + pct);
    }
  }
}
