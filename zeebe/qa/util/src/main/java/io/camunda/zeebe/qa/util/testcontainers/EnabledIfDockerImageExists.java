/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.testcontainers;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.apiguardian.api.API;
import org.junit.jupiter.api.extension.ExtendWith;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(EnabledForDockerImageCondition.class)
public @interface EnabledIfDockerImageExists {

  /** The Docker image name to check for existence. */
  String value() default "";

  /**
   * Custom reason to provide if the test or container is disabled.
   *
   * <p>If a custom reason is supplied, it will be combined with the default reason for this
   * annotation. If a custom reason is not supplied, the default reason will be used.
   *
   * @since 5.7
   */
  String disabledReason() default "";
}
