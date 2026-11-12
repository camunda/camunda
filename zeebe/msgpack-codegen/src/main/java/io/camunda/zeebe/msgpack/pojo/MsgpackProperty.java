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
 * Optional. Every non-static field of a {@link MsgpackPojo} is stored under its field name unless
 * it is annotated with {@link MsgpackIgnore}; this annotation only overrides the key or marks a raw
 * value. Supported field types: {@code long}, {@code int}, {@code boolean}, {@code DirectBuffer}
 * (msgpack string, or an already encoded msgpack value if {@link #raw()}), enums (stored by {@code
 * toString()}), {@code List<DirectBuffer>} (array of strings), {@code List<Long>} (array of
 * integers), other {@link MsgpackPojo} types and {@code io.camunda.zeebe.msgpack.value.BaseValue}s
 * (e.g. an {@code UnpackedObject}, embedded as a nested map that references the source buffer).
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface MsgpackProperty {
  /** The map key. Defaults to the field name. */
  String value() default "";

  /** The {@code DirectBuffer} holds a complete encoded msgpack value that is embedded as is. */
  boolean raw() default false;

  /**
   * The {@code DirectBuffer} is stored as a msgpack binary instead of a string. It is read as a
   * view on the source buffer, without copying.
   */
  boolean binary() default false;
}
