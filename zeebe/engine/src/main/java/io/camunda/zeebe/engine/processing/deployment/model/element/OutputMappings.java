/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * The transformed output mappings of a flow node: one entry per {@code zeebe:output} element, in
 * modeling order, plus the pre-parsed combined FEEL context expression used by {@code
 * CombinedOutputMappingResolver} to evaluate all mappings in a single call against the outer scope.
 *
 * <p>{@code combinedExpression} is the pre-parsed FEEL context literal built from all mappings at
 * deploy/load time for {@code CombinedOutputMappingResolver} to evaluate on each completion without
 * rebuilding it. Deploy-time parsing rejects invalid FEEL by throwing, so by the time this record
 * exists the field is always a valid expression.
 */
@NullMarked
public record OutputMappings(Expression combinedExpression, List<OutputMapping> mappings) {}
