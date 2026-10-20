/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.AutomationRateBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.CycleTimeBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.service.db.repository.BusinessValueOverviewRepository;
import io.camunda.optimize.service.exceptions.OptimizeBulkFailureException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class BusinessValueOverviewWriter {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(BusinessValueOverviewWriter.class);

  private final BusinessValueOverviewRepository repository;

  public BusinessValueOverviewWriter(final BusinessValueOverviewRepository repository) {
    this.repository = repository;
  }

  public void bulkUpsertFromScheduler(final List<BusinessValueOverviewDto> rows) {
    validateAll(rows);
    LOG.debug("Upserting [{}] business-value overview rows from scheduler", rows.size());
    repository.bulkUpsert(rows, false);
  }

  public void bulkUpsertFromTargetWrite(final List<BusinessValueOverviewDto> rows) {
    validateAll(rows);
    LOG.debug("Upserting [{}] business-value overview rows from target write", rows.size());
    repository.bulkUpsert(rows, true);
  }

  /**
   * Removes every overview row of a process definition — one per {@link MetricRange}.
   *
   * <p>Rows are keyed on {@code (tenantId, processDefinitionKey, metricRange)} and carry no
   * version, so this is only correct once the definition is gone for that tenant entirely. The ids
   * are computed rather than searched for, so a definition with no rows costs no query and deleting
   * rows that were never written is a no-op.
   *
   * <p>A blank tenant or key means there can be no rows to delete — neither can be written without
   * both — so this returns rather than failing the caller.
   */
  public void deleteForDefinition(final String tenantId, final String processDefinitionKey) {
    if (StringUtils.isBlank(tenantId) || StringUtils.isBlank(processDefinitionKey)) {
      LOG.debug(
          "Skipping business-value overview deletion for definition [{}] tenant [{}]: "
              + "rows cannot exist without both",
          processDefinitionKey,
          tenantId);
      return;
    }
    LOG.debug(
        "Deleting business-value overview rows for definition [{}] tenant [{}]",
        processDefinitionKey,
        tenantId);
    try {
      repository.deleteByIds(
          Arrays.stream(MetricRange.values())
              .map(
                  range ->
                      BusinessValueOverviewRepository.documentId(
                          tenantId, processDefinitionKey, range))
              .toList());
    } catch (final OptimizeRuntimeException e) {
      // Logged here as well as rethrown: the caller retries, so a failure that later succeeds
      // leaves no other trace that the cluster rejected the delete.
      LOG.warn(
          "Bulk delete of business-value overview rows for definition [{}] tenant [{}] failed",
          processDefinitionKey,
          tenantId,
          e);
      throw new OptimizeBulkFailureException(
          String.format(
              "Could not delete the business-value overview rows for definition [%s] tenant [%s].",
              processDefinitionKey, tenantId),
          e);
    }
  }

  private void validateAll(final List<BusinessValueOverviewDto> rows) {
    if (rows == null) {
      throw new IllegalArgumentException("rows must not be null");
    }
    for (final BusinessValueOverviewDto row : rows) {
      validate(row);
    }
  }

  private void validate(final BusinessValueOverviewDto row) {
    if (row == null) {
      throw new IllegalArgumentException("business-value overview row must not be null");
    }
    if (StringUtils.isBlank(row.getTenantId())) {
      throw new IllegalArgumentException(
          "tenantId must not be null or blank on a business-value overview row");
    }
    if (StringUtils.isBlank(row.getProcessDefinitionKey())) {
      throw new IllegalArgumentException(
          "processDefinitionKey must not be null or blank on a business-value overview row");
    }
    if (row.getMetricRange() == null) {
      throw new IllegalArgumentException(
          "metricRange must not be null on a business-value overview row");
    }
    if (row.getLastComputedAt() == null) {
      throw new IllegalArgumentException(
          "lastComputedAt must not be null on a business-value overview row");
    }

    final int expectedTargetsSet =
        countNonNullTarget(row.getCycleTime()) + countNonNullTarget(row.getAutomationRate());
    if (row.getTargetsSet() != expectedTargetsSet) {
      throw new IllegalArgumentException(
          "targetsSet ["
              + row.getTargetsSet()
              + "] does not match the number of non-null targets ["
              + expectedTargetsSet
              + "]");
    }
    if (row.isHasAnyTarget() != (expectedTargetsSet > 0)) {
      throw new IllegalArgumentException(
          "hasAnyTarget ["
              + row.isHasAnyTarget()
              + "] is inconsistent with targetsSet ["
              + expectedTargetsSet
              + "]");
    }

    final int expectedTargetsMet = countMet(row.getCycleTime()) + countMet(row.getAutomationRate());
    if (row.getTargetsMet() != expectedTargetsMet) {
      throw new IllegalArgumentException(
          "targetsMet ["
              + row.getTargetsMet()
              + "] does not match the number of met targets ["
              + expectedTargetsMet
              + "]");
    }

    validateBlockInvariant(
        "cycleTime",
        row.getCycleTime() == null ? null : row.getCycleTime().getValue(),
        row.getCycleTime() == null ? null : row.getCycleTime().getTarget(),
        row.getCycleTime() == null ? null : row.getCycleTime().getMet());
    validateBlockInvariant(
        "automationRate",
        row.getAutomationRate() == null ? null : row.getAutomationRate().getValue(),
        row.getAutomationRate() == null ? null : row.getAutomationRate().getTarget(),
        row.getAutomationRate() == null ? null : row.getAutomationRate().getMet());
  }

  private static int countNonNullTarget(final CycleTimeBlock block) {
    return block != null && block.getTarget() != null ? 1 : 0;
  }

  private static int countNonNullTarget(final AutomationRateBlock block) {
    return block != null && block.getTarget() != null ? 1 : 0;
  }

  private static int countMet(final CycleTimeBlock block) {
    return block != null && Boolean.TRUE.equals(block.getMet()) ? 1 : 0;
  }

  private static int countMet(final AutomationRateBlock block) {
    return block != null && Boolean.TRUE.equals(block.getMet()) ? 1 : 0;
  }

  private static void validateBlockInvariant(
      final String kpi, final Object value, final Object target, final Boolean met) {
    final boolean unset = value == null || target == null;
    if (unset && met != null) {
      throw new IllegalArgumentException(kpi + ".met must be null when value or target is null");
    }
    if (!unset && met == null) {
      throw new IllegalArgumentException(
          kpi + ".met must be non-null when value and target are both non-null");
    }
  }
}
