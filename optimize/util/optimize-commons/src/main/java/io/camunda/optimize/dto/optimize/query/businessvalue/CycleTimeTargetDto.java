/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.businessvalue;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Shared nested shape for a cycle-time target on the REST surface. Requests carry {@code value} and
 * {@code unit}; the server recomputes {@code millis} from those and echoes all three on the
 * response so the frontend can pass the native-unit value straight into the verdict function
 * without duplicating the unit-conversion table.
 *
 * <p>Any {@code millis} value sent on a request body is ignored — the pair {@code (value, unit)} is
 * the source of truth on writes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CycleTimeTargetDto(
    @PositiveOrZero(message = "cycleTimeTarget.value must be non-negative") Long value,
    TargetValueUnit unit,
    Long millis) {}
