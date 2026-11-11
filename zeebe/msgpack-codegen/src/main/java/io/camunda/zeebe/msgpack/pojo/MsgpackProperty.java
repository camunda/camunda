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
 * A property stored under {@link #value()} as map key. Supported field types: {@code long}, {@code
 * int}, {@code boolean}, {@code DirectBuffer} (msgpack string, or an already encoded msgpack value
 * if {@link #raw()}), enums (stored by {@code toString()}), {@code List<DirectBuffer>} (array of
 * strings) and other {@link MsgpackPojo} types.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface MsgpackProperty {
  String value();

  /** The {@code DirectBuffer} holds a complete encoded msgpack value that is embedded as is. */
  boolean raw() default false;
}
