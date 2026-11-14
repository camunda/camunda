/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import io.camunda.zeebe.msgpack.Recyclable;

/**
 * The operations on a {@code T} besides reading and writing it, such as restoring the field
 * initializers of its {@link MsgpackPojo} class. Generated as {@code <Name>Ops}, like the {@link
 * MsgpackCodec}, and stateless.
 *
 * <p>The generated class also has them as static methods, which generated code calls directly where
 * the type is known. This interface is for code that is generic in the type, such as {@link
 * MsgpackBufferValue}.
 */
public interface MsgpackOps<T extends Recyclable> {

  /** Restores the field initializers. */
  void reset(T target);

  /**
   * Copies the stored properties of {@code source} into {@code target}, which loses its previous
   * ones. The copy is deep: it shares no state with the source, and it does not look at the buffer
   * the source was read from. Fields that are not stored ({@link MsgpackIgnore}) are left alone.
   */
  void copy(T source, T target);

  /**
   * Whether both have the same stored properties, compared by content: buffers by their bytes,
   * lists and nested values by their elements. {@link MsgpackIgnore}d fields are not compared.
   */
  boolean equals(T a, T b);

  /**
   * A hash code of the stored properties, consistent with {@link #equals(Recyclable, Recyclable)}.
   */
  int hashCode(T value);

  /**
   * The stored properties as JSON, like {@code ObjectValue#toString}: {@code {"key":value,...}}
   * with strings and enums quoted, binaries and documents as quoted base64, raw values as {@code
   * [packed value (length=n)]}, and the entries of unknown properties in the same form after the
   * declared ones. Properties marked {@link MsgpackSanitized} print {@code "***"}.
   */
  String toString(T value);
}
