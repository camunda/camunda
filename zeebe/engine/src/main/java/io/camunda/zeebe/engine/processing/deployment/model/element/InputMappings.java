/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * The transformed input mappings of a flow node: the FEEL expression that produces the local
 * variables, plus the secret references detected in it, keyed by the JSON pointer (RFC 6901) of the
 * leaf each secret belongs to (e.g. {@code /tokens/token}). {@code secretReferences} is empty when
 * no input mapping references a secret.
 */
@NullMarked
public record InputMappings(
    Expression expression, Map<String, Set<SecretReference>> secretReferences) {}
