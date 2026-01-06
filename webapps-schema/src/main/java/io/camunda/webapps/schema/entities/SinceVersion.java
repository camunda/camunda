/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * New fields in entities stored at {@link io.camunda.webapps.schema.entities} must have the
 * annotation {@link io.camunda.webapps.schema.entities.SinceVersion} with the value being the
 * semantic version the field was introduced.
 *
 * <p>In addition, all fields with this annotation must have a non-null default value. This is to
 * maintain backwards compatability. However, if the nullable field is set to true then it is
 * acceptable for the field to have a null default.
 *
 * <p>Example usage:
 *
 * <pre><code>
 *   class Foo {
 *     private String newField; // This will fail
 *
 *    {@literal @SinceVersion("8.8.0")}
 *     private String newField = "bar" // This will pass
 *
 *    {@literal @SinceVersion(value = "8.8.0", nullable = true)}
 *     private String newField; // This will pass
 *   }
 * </code></pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SinceVersion {
  String value();

  boolean nullable() default false;
}
