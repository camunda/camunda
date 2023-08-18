/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
 * Registers the {@link AutoCloseResourceExtension} extension, which will certain fields are closed
 * after their lifecycle is finished.
 *
 * <p>It will close any static or instance fields annotated with {@link AutoCloseResource}. If
 * {@link #onlyAnnotated()} is false (the default), it will also close any {@link AutoCloseable}
 * static or instance fields.
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(AutoCloseResourceExtension.class)
public @interface AutoCloseResources {

  /**
   * If true, will only close resources which are annotated with {@link AutoCloseResource}.
   * Otherwise, it will close these and also any {@link AutoCloseable} it finds.
   */
  boolean onlyAnnotated() default true;

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
