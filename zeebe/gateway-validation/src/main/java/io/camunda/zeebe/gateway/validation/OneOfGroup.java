/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.validation;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation to validate that an object matches exactly one branch of a named domain oneOf group.
 * Group identifiers correspond to build-time extracted definitions from the domain OpenAPI spec.
 */
@Documented
@Target({PARAMETER, FIELD, TYPE_USE})
@Retention(RUNTIME)
@Constraint(validatedBy = OneOfGroupValidator.class)
public @interface OneOfGroup {
  /** Required group identifier referencing a generated descriptor. */
  String value();

  /** Whether unexpected (non-allowed and non-operator) properties cause failure. */
  boolean strictExtra() default false;

  /** Enforce original JSON token kind vs expected primitive kind (requires capture). */
  boolean strictTokenKinds() default false;

  /** Enable raw token capture for this parameter (if infrastructure present). */
  boolean captureRawTokens() default false;

  /** Fail when multiple branches remain after disambiguation heuristics. */
  boolean failOnAmbiguous() default false;

  String message() default "{validation.oneofgroup}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
