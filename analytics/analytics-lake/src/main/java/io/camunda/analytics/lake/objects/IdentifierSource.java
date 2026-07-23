/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.objects;

/**
 * One way a {@link CompiledObjectType} recognizes an instance of itself in the Zeebe record stream
 * — see {@link ObjectTypes#variable(String)}/{@link ObjectTypes#correlationKey(String)} for the two
 * concrete sources and {@code io.camunda.analytics.lake.translate.LakeTranslator}'s own "Object
 * fabric capture" javadoc section for how each is matched against a record.
 */
public sealed interface IdentifierSource {

  /**
   * Identifies an object by a root- or non-root-scope process variable named {@code variableName}:
   * a {@code VARIABLE CREATED}/{@code UPDATED} record whose {@code name} equals this, and whose
   * value is a JSON scalar string or number, sights that value as this type's object id (see {@code
   * LakeTranslator}'s own scalar-value rule).
   */
  record VariableIdentifier(String variableName) implements IdentifierSource {}

  /**
   * Identifies an object by message-correlation key: <b>any</b> correlated message subscription
   * (process-instance-side or message-start-event-side) whose correlation key is non-blank sights
   * that key as this type's object id — {@code label} is descriptive only (the correlated record
   * carries the key's <em>value</em>, never which variable produced it, so there is nothing to
   * match {@code label} against; see {@link CompiledObjectTypes#of} for the fail-fast rule this
   * imprecision requires: at most one declared object type may claim correlation-key identity).
   */
  record CorrelationKeyIdentifier(String label) implements IdentifierSource {}
}
