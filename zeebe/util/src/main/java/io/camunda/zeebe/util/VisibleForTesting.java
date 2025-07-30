/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the visibility was strengthened purely for testing purposes.
 *
 * <p>NOTE: this should not be used on public members, but is meant only to highlight
 * package-private or protected fields, types, etc., which aren't private in order to allow
 * comprehensive tests. In general, you should try to avoid this, but at times there is no other
 * way.
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface VisibleForTesting {

  /** An optional justification as to why something was made visible for testing */
  String value() default "";
}
