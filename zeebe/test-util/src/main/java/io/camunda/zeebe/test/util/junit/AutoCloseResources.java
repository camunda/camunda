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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Registers the {@link AutoCloseResourceExtension} extension, which will all fields annotated with
 * {@link AutoCloseResource} and ensure they closed after their lifecycle is finished.
 *
 * <p>This means, instance fields are closed after every test, and static fields after all tests.
 * The order in which the fields are closed is undefined.
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(AutoCloseResourceExtension.class)
public @interface AutoCloseResources {

  /**
   * Indicates a resource which may be closed by the {@link AutoCloseResourceExtension} extension.
   */
  @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @Inherited
  @interface AutoCloseResource {
    String closeMethod() default "close";
  }
}
