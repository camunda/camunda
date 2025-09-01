/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.validation.model;

/**
 * Immutable branch metadata capturing minimal structural info for validation.
 *
 * <p>Future: replace string arrays with dictionary indexes & bitsets for performance.
 */
public final class BranchDescriptor {
  private final int id;
  private final int specificity;
  private final String[] required;
  private final String[] optional;
  private final EnumLiteral[][] enumLiteralsPerProperty; // parallel to required+optional order
  private final PatternDescriptor[] patterns; // patterns for properties (subset)

  public BranchDescriptor(
      final int id,
      final int specificity,
      final String[] required,
      final String[] optional,
      final EnumLiteral[][] enumLiteralsPerProperty,
      final PatternDescriptor[] patterns) {
    this.id = id;
    this.specificity = specificity;
    this.required = required;
    this.optional = optional;
    this.enumLiteralsPerProperty = enumLiteralsPerProperty;
    this.patterns = patterns;
  }

  /** Convenience / backward compatibility constructor used by older generated code. */
  public BranchDescriptor(final int id, final int specificity) {
    this(id, specificity, new String[0], new String[0], new EnumLiteral[0][], new PatternDescriptor[0]);
  }

  public int id() {
    return id;
  }

  public int specificity() {
    return specificity;
  }

  public String[] required() {
    return required;
  }

  public String[] optional() {
    return optional;
  }

  public EnumLiteral[][] enumLiteralsPerProperty() {
    return enumLiteralsPerProperty;
  }

  public PatternDescriptor[] patterns() {
    return patterns;
  }
}
