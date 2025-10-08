/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares classpath resources to be deployed before running the process tests. The annotation can
 * be applied at the class level or to individual test methods. If present, method-level annotations
 * take precedence over class-level annotations.
 *
 * <p>Example usage:
 *
 * <pre>
 * &#064;TestDeployment(resources = "process1.bpmn")
 * &#064;Test
 * void test1() {
 *   // The process is deployed before the test run.
 * }
 *
 * &#064;TestDeployment(resources = {"process1.bpmn", "decision.dmn"})
 * &#064;Test
 * void test2() {
 *   // All kind of resources can be deployed.
 * }
 * </pre>
 *
 * @see CamundaProcessTest
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TestDeployment {

  /**
   * The deployment resources (BPMN, DMN, etc.) to be deployed before running the process tests. The
   * resources are loaded from the root classpath.
   *
   * @return an array of classpath resources
   */
  String[] resources() default {};
}
