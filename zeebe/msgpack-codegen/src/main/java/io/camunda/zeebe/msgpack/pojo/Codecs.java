/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.BaseValue;

/**
 * Finds the {@link MsgpackCodec} of a class: the generated {@code <Name>Codec} of a {@link
 * MsgpackPojo}, or an adapter for a {@link BaseValue} (e.g. an {@code UnpackedObject}). Only exact
 * classes are supported. Generated code resolves the codecs of its nested objects once, when it is
 * initialized, so this is not on the hot path.
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
          try {
            final var codecClass =
                Class.forName(type.getName() + "Codec", true, type.getClassLoader());
            return (MsgpackCodec<?>) codecClass.getField("INSTANCE").get(null);
          } catch (final ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                "No msgpack codec for %s: it is neither a @MsgpackPojo nor a BaseValue"
                    .formatted(type.getName()),
                e);
          }
        }
      };

  private Codecs() {}

  @SuppressWarnings("unchecked")
  public static <T> MsgpackCodec<T> of(final Class<T> type) {
    return (MsgpackCodec<T>) CODECS.get(type);
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
    public void reset(final BaseValue target) {
      target.reset();
    }

    @Override
    public void read(final MsgPackReader reader, final BaseValue target) {
      target.read(reader);
    }
  }
}
