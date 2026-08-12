/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.businessvalue;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.util.List;

/**
 * Assembled L0 target-overview response for {@code GET /business-value/overview?range=<preset>}.
 * Shape mirrors {@code bvd-target-technical-design.md} §5.3: an attainment rollup plus two per-KPI
 * category donuts (cycle time + automation rate; volume is descoped in v1) plus a flat off-target
 * list sorted by {@code gapPct} descending.
 *
 * <p>Cycle-time {@link OffTargetEntryDto#value} / {@link OffTargetEntryDto#target} are emitted in
 * their native storage unit (milliseconds); {@code displayUnit} is the FE formatting hint.
 */
public record BusinessValueOverviewResponseDto(
    boolean hasAnyTarget,
    CoverageDto coverage,
    AttainmentDto attainment,
    List<CategoryDto> categories,
    List<OffTargetEntryDto> offTarget)
    implements OptimizeDto {

  public record CoverageDto(int processesWithTarget, int totalProcesses) {}

  public record AttainmentDto(int targetsSet, int targetsMet) {}

  public record CategoryDto(
      String kpi, int processesWithTarget, int totalApplicable, int targetsMet) {}

  public record OffTargetEntryDto(
      String tenantId,
      String processKey,
      String processName,
      String kpi,
      double value,
      double target,
      String displayUnit,
      double gapPct,
      String direction) {}
}
