/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a plain, top-level data class for which a stateless {@code <Name>Codec} and {@code
 * <Name>Resetter} are generated in the same package. Its superclasses must not declare stored
 * fields: only the fields of the annotated class are stored.
 *
 * <p>Retained in the class file, so a {@code @MsgpackPojo} of another module can be nested.
 *
 * <p>The class needs a no-arg constructor that is accessible from the same package. Defaults are
 * the field initializers, and the class must be {@link io.camunda.zeebe.msgpack.Recyclable}: its
 * {@code reset()} is called before a value is read, so properties missing in the input keep their
 * default. Compilation fails otherwise. The generated {@code <Name>Resetter.reset(target)} restores
 * all defaults, so {@code reset()} is normally just that call. The generated code reads and writes
 * the fields directly, so they must not be private; scalar, string, enum and raw fields must not be
 * final, except for a {@code final UnsafeBuffer}, which is wrapped in place instead of being
 * replaced by a new view on every read. Every non-static field is stored unless annotated with
 * {@link MsgpackIgnore}. Properties are written in field declaration order and keyed by field name
 * (keys must be unique), both of which are part of the stored format.
 *
 * <p>String, binary and raw fields need an initializer, which is the default of every instance: the
 * default buffer is shared, so it must never be modified.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface MsgpackPojo {}
