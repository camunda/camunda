/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.businessvalue;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.Objects;

/**
 * Assembled L0 target-overview response for {@code GET /business-value/overview?range=<preset>}.
 * Shape mirrors {@code bvd-target-technical-design.md} §5.3: an attainment rollup plus two per-KPI
 * category donuts (cycle time + automation rate; volume is descoped in v1) plus a flat off-target
 * list carrying every KPI whose target is not met: measured misses first, sorted by {@code gapPct}
 * descending, then the entries that have no measurement to put a gap on (see {@link
 * OffTargetStatus}).
 *
 * <p>Cycle-time {@link OffTargetEntryDto#getValue()} / {@link OffTargetEntryDto#getTarget()} are
 * emitted in their native storage unit (milliseconds); {@code displayUnit} is the FE formatting
 * hint.
 *
 * <p>Response size is bounded by the repository-level fetch cap (see {@code
 * BusinessValueOverviewReadService}); a response never silently drops rows.
 */
public class BusinessValueOverviewResponseDto {

  private boolean hasAnyTarget;
  private CoverageDto coverage;
  private AttainmentDto attainment;
  private List<CategoryDto> categories;
  private List<OffTargetEntryDto> offTarget;

  public BusinessValueOverviewResponseDto() {}

  public BusinessValueOverviewResponseDto(
      final boolean hasAnyTarget,
      final CoverageDto coverage,
      final AttainmentDto attainment,
      final List<CategoryDto> categories,
      final List<OffTargetEntryDto> offTarget) {
    this.hasAnyTarget = hasAnyTarget;
    this.coverage = coverage;
    this.attainment = attainment;
    this.categories = categories;
    this.offTarget = offTarget;
  }

  public boolean isHasAnyTarget() {
    return hasAnyTarget;
  }

  public void setHasAnyTarget(final boolean hasAnyTarget) {
    this.hasAnyTarget = hasAnyTarget;
  }

  public CoverageDto getCoverage() {
    return coverage;
  }

  public void setCoverage(final CoverageDto coverage) {
    this.coverage = coverage;
  }

  public AttainmentDto getAttainment() {
    return attainment;
  }

  public void setAttainment(final AttainmentDto attainment) {
    this.attainment = attainment;
  }

  public List<CategoryDto> getCategories() {
    return categories;
  }

  public void setCategories(final List<CategoryDto> categories) {
    this.categories = categories;
  }

  public List<OffTargetEntryDto> getOffTarget() {
    return offTarget;
  }

  public void setOffTarget(final List<OffTargetEntryDto> offTarget) {
    this.offTarget = offTarget;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BusinessValueOverviewResponseDto that = (BusinessValueOverviewResponseDto) o;
    return hasAnyTarget == that.hasAnyTarget
        && Objects.equals(coverage, that.coverage)
        && Objects.equals(attainment, that.attainment)
        && Objects.equals(categories, that.categories)
        && Objects.equals(offTarget, that.offTarget);
  }

  @Override
  public int hashCode() {
    return Objects.hash(hasAnyTarget, coverage, attainment, categories, offTarget);
  }

  @Override
  public String toString() {
    return "BusinessValueOverviewResponseDto(hasAnyTarget="
        + hasAnyTarget
        + ", coverage="
        + coverage
        + ", attainment="
        + attainment
        + ", categories="
        + categories
        + ", offTarget="
        + offTarget
        + ")";
  }

  public static class CoverageDto {

    private int processesWithTarget;
    private int totalProcesses;

    public CoverageDto() {}

    public CoverageDto(final int processesWithTarget, final int totalProcesses) {
      this.processesWithTarget = processesWithTarget;
      this.totalProcesses = totalProcesses;
    }

    public int getProcessesWithTarget() {
      return processesWithTarget;
    }

    public void setProcessesWithTarget(final int processesWithTarget) {
      this.processesWithTarget = processesWithTarget;
    }

    public int getTotalProcesses() {
      return totalProcesses;
    }

    public void setTotalProcesses(final int totalProcesses) {
      this.totalProcesses = totalProcesses;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final CoverageDto that = (CoverageDto) o;
      return processesWithTarget == that.processesWithTarget
          && totalProcesses == that.totalProcesses;
    }

    @Override
    public int hashCode() {
      return Objects.hash(processesWithTarget, totalProcesses);
    }

    @Override
    public String toString() {
      return "CoverageDto(processesWithTarget="
          + processesWithTarget
          + ", totalProcesses="
          + totalProcesses
          + ")";
    }
  }

  public static class AttainmentDto {

    private int targetsSet;
    private int targetsMet;

    public AttainmentDto() {}

    public AttainmentDto(final int targetsSet, final int targetsMet) {
      this.targetsSet = targetsSet;
      this.targetsMet = targetsMet;
    }

    public int getTargetsSet() {
      return targetsSet;
    }

    public void setTargetsSet(final int targetsSet) {
      this.targetsSet = targetsSet;
    }

    public int getTargetsMet() {
      return targetsMet;
    }

    public void setTargetsMet(final int targetsMet) {
      this.targetsMet = targetsMet;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final AttainmentDto that = (AttainmentDto) o;
      return targetsSet == that.targetsSet && targetsMet == that.targetsMet;
    }

    @Override
    public int hashCode() {
      return Objects.hash(targetsSet, targetsMet);
    }

    @Override
    public String toString() {
      return "AttainmentDto(targetsSet=" + targetsSet + ", targetsMet=" + targetsMet + ")";
    }
  }

  /**
   * Per-KPI category rollup.
   *
   * <p>{@code totalApplicable} is the count of processes for which this KPI is a meaningful
   * measurement. In v1 both KPIs are universally applicable to every process, so {@code
   * totalApplicable == CoverageDto.totalProcesses} on every response — the field is kept as a
   * distinct member so the denominator can diverge once a KPI becomes conditionally applicable
   * (e.g. automation rate on a process with zero user tasks) without a breaking API change.
   */
  public static class CategoryDto {

    private String kpi;
    private int processesWithTarget;
    private int totalApplicable;
    private int targetsMet;

    public CategoryDto() {}

    public CategoryDto(
        final String kpi,
        final int processesWithTarget,
        final int totalApplicable,
        final int targetsMet) {
      this.kpi = kpi;
      this.processesWithTarget = processesWithTarget;
      this.totalApplicable = totalApplicable;
      this.targetsMet = targetsMet;
    }

    public String getKpi() {
      return kpi;
    }

    public void setKpi(final String kpi) {
      this.kpi = kpi;
    }

    public int getProcessesWithTarget() {
      return processesWithTarget;
    }

    public void setProcessesWithTarget(final int processesWithTarget) {
      this.processesWithTarget = processesWithTarget;
    }

    public int getTotalApplicable() {
      return totalApplicable;
    }

    public void setTotalApplicable(final int totalApplicable) {
      this.totalApplicable = totalApplicable;
    }

    public int getTargetsMet() {
      return targetsMet;
    }

    public void setTargetsMet(final int targetsMet) {
      this.targetsMet = targetsMet;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final CategoryDto that = (CategoryDto) o;
      return processesWithTarget == that.processesWithTarget
          && totalApplicable == that.totalApplicable
          && targetsMet == that.targetsMet
          && Objects.equals(kpi, that.kpi);
    }

    @Override
    public int hashCode() {
      return Objects.hash(kpi, processesWithTarget, totalApplicable, targetsMet);
    }

    @Override
    public String toString() {
      return "CategoryDto(kpi="
          + kpi
          + ", processesWithTarget="
          + processesWithTarget
          + ", totalApplicable="
          + totalApplicable
          + ", targetsMet="
          + targetsMet
          + ")";
    }
  }

  /**
   * Why a KPI's target is not met.
   *
   * <p>A target with no measured value is unmet — {@code targetsMet} has always counted it that way
   * — but it is unmet for a different reason than a measured miss, and there is no gap to quote. A
   * consumer needs the distinction to explain the verdict instead of printing a percentage it does
   * not have.
   */
  public enum OffTargetStatus {
    /**
     * Measured, and the value misses the target. {@code value}, {@code gapPct} and {@code
     * comparison} are all populated.
     */
    OFF_TARGET("offTarget"),
    /**
     * The sweep measured this definition and found no completed instances in the range, so the KPI
     * produced no bucket. Also covers a definition with no process-instance index at all, and —
     * acknowledged imprecision — one dropped by the aggregation bucket limit.
     */
    NO_COMPLETED_INSTANCES("noCompletedInstances"),
    /**
     * Instances completed, but the KPI does not apply to this process, so it can never produce a
     * value however long it runs. Automation rate is the only v1 KPI that can land here: it divides
     * over task flow nodes, so a process built only from events, gateways or sub-process containers
     * has nothing to automate and the interpreter returns null.
     */
    NOT_APPLICABLE("notApplicable"),
    /**
     * The target exists but no overview row does, so the definition has never been measured. In
     * practice that means a definition imported since the last sweep: once any sweep has written a
     * row for it, {@code alreadyComputed} stops a row being synthesized, and a target saved against
     * it waits for the next sweep instead of appearing here. Transient either way — the next
     * successful compute moves the entry to one of the other statuses.
     */
    NOT_MEASURED("notMeasured");

    private final String id;

    OffTargetStatus(final String id) {
      this.id = id;
    }

    @JsonValue
    public String getId() {
      return id;
    }
  }

  /**
   * One process/KPI pair whose target was set but not met.
   *
   * <p>{@code value} and {@code target} are emitted in the KPI's native storage unit — for
   * cycle-time entries that means milliseconds regardless of what {@code displayUnit} says. The FE
   * formats {@code value} and {@code target} for display using {@code displayUnit} as the hint
   * ({@code "HOURS"} for cycle time, {@code "PERCENT"} for automation rate).
   *
   * <p>{@code comparison} is the value-to-target relation label — either {@code "over"} or {@code
   * "under"}. It answers "did the observed value overshoot or undershoot the target?" and is
   * independent of whether the KPI is lower-is-better or higher-is-better; the FE combines {@code
   * comparison} with the KPI direction to render the copy ("22h vs 8h target, 175% over").
   *
   * <p>{@code value}, {@code gapPct} and {@code comparison} are null for every {@link
   * OffTargetStatus} other than {@link OffTargetStatus#OFF_TARGET} — there is no measurement to
   * report or to compute a gap from. {@code target} is never null: an entry is only emitted for a
   * KPI that has one.
   */
  public static class OffTargetEntryDto {

    private String tenantId;
    private String processKey;
    private String processName;
    private String kpi;
    private OffTargetStatus status;
    private Double value;
    private double target;
    private String displayUnit;
    private Double gapPct;
    private String comparison;

    public OffTargetEntryDto() {}

    public OffTargetEntryDto(
        final String tenantId,
        final String processKey,
        final String processName,
        final String kpi,
        final OffTargetStatus status,
        final Double value,
        final double target,
        final String displayUnit,
        final Double gapPct,
        final String comparison) {
      this.tenantId = tenantId;
      this.processKey = processKey;
      this.processName = processName;
      this.kpi = kpi;
      this.status = status;
      this.value = value;
      this.target = target;
      this.displayUnit = displayUnit;
      this.gapPct = gapPct;
      this.comparison = comparison;
    }

    public String getTenantId() {
      return tenantId;
    }

    public void setTenantId(final String tenantId) {
      this.tenantId = tenantId;
    }

    public String getProcessKey() {
      return processKey;
    }

    public void setProcessKey(final String processKey) {
      this.processKey = processKey;
    }

    public String getProcessName() {
      return processName;
    }

    public void setProcessName(final String processName) {
      this.processName = processName;
    }

    public String getKpi() {
      return kpi;
    }

    public void setKpi(final String kpi) {
      this.kpi = kpi;
    }

    public OffTargetStatus getStatus() {
      return status;
    }

    public void setStatus(final OffTargetStatus status) {
      this.status = status;
    }

    public Double getValue() {
      return value;
    }

    public void setValue(final Double value) {
      this.value = value;
    }

    public double getTarget() {
      return target;
    }

    public void setTarget(final double target) {
      this.target = target;
    }

    public String getDisplayUnit() {
      return displayUnit;
    }

    public void setDisplayUnit(final String displayUnit) {
      this.displayUnit = displayUnit;
    }

    public Double getGapPct() {
      return gapPct;
    }

    public void setGapPct(final Double gapPct) {
      this.gapPct = gapPct;
    }

    public String getComparison() {
      return comparison;
    }

    public void setComparison(final String comparison) {
      this.comparison = comparison;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final OffTargetEntryDto that = (OffTargetEntryDto) o;
      return Double.compare(target, that.target) == 0
          && Objects.equals(value, that.value)
          && Objects.equals(gapPct, that.gapPct)
          && Objects.equals(tenantId, that.tenantId)
          && Objects.equals(processKey, that.processKey)
          && Objects.equals(processName, that.processName)
          && Objects.equals(kpi, that.kpi)
          && status == that.status
          && Objects.equals(displayUnit, that.displayUnit)
          && Objects.equals(comparison, that.comparison);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          tenantId,
          processKey,
          processName,
          kpi,
          status,
          value,
          target,
          displayUnit,
          gapPct,
          comparison);
    }

    @Override
    public String toString() {
      return "OffTargetEntryDto(tenantId="
          + tenantId
          + ", processKey="
          + processKey
          + ", processName="
          + processName
          + ", kpi="
          + kpi
          + ", status="
          + status
          + ", value="
          + value
          + ", target="
          + target
          + ", displayUnit="
          + displayUnit
          + ", gapPct="
          + gapPct
          + ", comparison="
          + comparison
          + ")";
    }
  }
}
