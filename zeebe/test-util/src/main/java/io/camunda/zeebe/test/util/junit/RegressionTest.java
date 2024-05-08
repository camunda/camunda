/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.junit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

/**
 * {@code @RegressionTest} is used to signal that the annotated method is a <em>test</em> method,
 * written specifically to address a regression bug. It should therefore be linked to an issue
 * tracker URL, which is the expected and <em>required</em> value.
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Test
public @interface RegressionTest {

  /** The GitHub issue URL where the regression is described, and for which this is a test. */
  String value();
}
