/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson.record;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is required to notify the Immutable's code generator that there in the collection
 * (both {@link java.util.Collection} and {@link java.util.Map}) could be nullable values inside it
 * (in terms of {@link java.util.Map} - the nullable values, not <strong>keys</strong>)
 *
 * @see <a href="https://immutables.github.io/immutable.html#nulls-in-collection">Immutables
 *     approach to allow nullable values in the collection</a>
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface AllowNulls {}
