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
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * The transformed input mappings of a flow node: one entry per {@code zeebe:input} element, in
 * modeling order, plus the secret references detected in them, keyed by the JSON pointer (RFC 6901)
 * of the leaf each secret belongs to (e.g. {@code /tokens/token}). {@code secretReferences} is
 * empty when no input mapping references a secret.
 *
 * <p>{@code clusterVariableReferences} mirrors {@code secretReferences} for cluster-variable
 * references ({@code camunda.vars.<scope>.<name>}) detected in the input mappings, keyed by the
 * same RFC-6901 leaf JSON pointer; empty when no input mapping references a cluster variable.
 *
 * <p>{@code combinedExpression} is the pre-parsed FEEL context literal built from all mappings at
 * deploy/load time for {@code CombinedMappingResolver} to evaluate on each activation without
 * rebuilding it. Deploy-time parsing rejects invalid FEEL by throwing, so by the time this record
 * exists the field is always a valid expression.
 */
@NullMarked
public record InputMappings(
    List<InputMapping> mappings,
    Expression combinedExpression,
    Map<String, Set<SecretReference>> secretReferences,
    Map<String, Set<ClusterVariableReference>> clusterVariableReferences) {}
