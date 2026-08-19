/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.businessvalue;

import java.time.OffsetDateTime;

/**
 * Response body for {@code GET /business-value/targets/{processDefinitionKey}?tenantId=…} and its
 * {@code PUT} counterpart. When no target document exists yet for the pair, every nullable field is
 * {@code null} and callers still receive a fully-populated {@code tenantId} + {@code processKey}
 * echo of their request.
 */
public record BusinessValueTargetResponseDto(
    String tenantId,
    String processKey,
    CycleTimeTargetDto cycleTimeTarget,
    Integer automationRateTargetPct,
    OffsetDateTime updatedAt,
    String updatedBy) {}
