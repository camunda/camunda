/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import io.camunda.zeebe.msgpack.Recyclable;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.BaseValue;

/**
 * Finds the {@link MsgpackCodec} of a class: the generated {@code <Name>Codec} of a {@link
 * MsgpackPojo}, or an adapter for a {@link BaseValue} (e.g. an {@code UnpackedObject}). Only exact
 * classes are supported. Look-ups are not meant for the hot path: generated code resolves the
 * codecs of its nested objects once, when it is initialized, and {@link MsgpackBufferValue} once
 * per instance.
 */
public final class Codecs {

  private static final MsgpackCodec<BaseValue> BASE_VALUE_CODEC = new BaseValueCodec();

  private static final ClassValue<MsgpackCodec<?>> CODECS =
      new ClassValue<>() {
        @Override
        protected MsgpackCodec<?> computeValue(final Class<?> type) {
          if (BaseValue.class.isAssignableFrom(type)) {
            return BASE_VALUE_CODEC;
          }
          return generated(type, "Codec", MsgpackCodec.class);
        }
      };

  private static final ClassValue<MsgpackOps<?>> OPS =
      new ClassValue<>() {
        @Override
        protected MsgpackOps<?> computeValue(final Class<?> type) {
          return generated(type, "Ops", MsgpackOps.class);
        }
      };

  private Codecs() {}

  @SuppressWarnings("unchecked")
  public static <T extends Recyclable> MsgpackCodec<T> of(final Class<T> type) {
    return (MsgpackCodec<T>) CODECS.get(type);
  }

  /** Finds the generated {@code <Name>Ops}, which restores the field initializers. */
  @SuppressWarnings("unchecked")
  public static <T extends Recyclable> MsgpackOps<T> opsOf(final Class<T> type) {
    return (MsgpackOps<T>) OPS.get(type);
  }

  private static <R> R generated(final Class<?> type, final String suffix, final Class<R> role) {
    try {
      final var generated = Class.forName(type.getName() + suffix, true, type.getClassLoader());
      final var instance = generated.getField("INSTANCE").get(null);
      if (instance == null) {
        // ClassValue would cache null for good
        throw new IllegalStateException(
            "%s.INSTANCE is not initialized yet: is it resolved during its own class initialization?"
                .formatted(generated.getName()));
      }
      return role.cast(instance);
    } catch (final ReflectiveOperationException e) {
      throw new IllegalArgumentException(
          "No msgpack %s for %s: it is neither a @MsgpackPojo nor a BaseValue"
              .formatted(suffix.toLowerCase(), type.getName()),
          e);
    }
  }

  /** Codec of {@code BaseValue}s, which can already read and write themselves. */
  private static final class BaseValueCodec implements MsgpackCodec<BaseValue> {

    @Override
    public int write(final MsgPackWriter writer, final BaseValue value) {
      return value.write(writer);
    }

    @Override
    public int getLength(final BaseValue value) {
      return value.getEncodedLength();
    }

    @Override
    public void read(final MsgPackReader reader, final BaseValue target) {
      target.read(reader);
    }
  }
}
