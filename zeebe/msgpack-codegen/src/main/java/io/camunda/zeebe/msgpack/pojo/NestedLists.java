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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.agrona.DirectBuffer;

/**
 * Operations on lists nested to any depth, such as {@code List<List<Long>>}, for generated code. A
 * level of nesting is an array, so one recursion over the {@code depth} serves every element type;
 * only the innermost elements need an {@link ElementCodec} or {@link ElementOps}. Not meant to be
 * used directly.
 *
 * <p>The lists are passed as {@code List<?>}: the generic type of the field is the contract of the
 * generated code, which also supplies the matching element operations.
 */
public final class NestedLists {

  private NestedLists() {}

  /** Writes, measures and reads the innermost elements. */
  public interface ElementCodec<T> {
    ElementCodec<Long> LONG =
        new ElementCodec<>() {
          @Override
          public int write(final MsgPackWriter writer, final Long value) {
            return writer.writeInteger(value);
          }

          @Override
          public int length(final Long value) {
            return MsgPackWriter.getEncodedLongValueLength(value);
          }

          @Override
          public Long read(final MsgPackReader reader) {
            return reader.readInteger();
          }
        };

    /** Strings, read as views on the source buffer like {@code List<DirectBuffer>}. */
    ElementCodec<DirectBuffer> STRING =
        new ElementCodec<>() {
          @Override
          public int write(final MsgPackWriter writer, final DirectBuffer value) {
            return writer.writeString(value);
          }

          @Override
          public int length(final DirectBuffer value) {
            return MsgPackWriter.getEncodedStringLength(value.capacity());
          }

          @Override
          public DirectBuffer read(final MsgPackReader reader) {
            return Decoding.readString(reader);
          }
        };

    int write(MsgPackWriter writer, T value);

    int length(T value);

    T read(MsgPackReader reader);
  }

  /** Copies, compares, hashes and prints the innermost elements. */
  public interface ElementOps<T> {
    ElementOps<Long> LONG =
        new ElementOps<>() {
          @Override
          public Long copy(final Long value) {
            return value;
          }

          @Override
          public void appendTo(final StringBuilder builder, final Long value) {
            builder.append(value);
          }

          @Override
          public boolean equal(final Long a, final Long b) {
            return a.equals(b);
          }

          @Override
          public int hash(final Long value) {
            return value.hashCode();
          }
        };

    ElementOps<DirectBuffer> STRING =
        new ElementOps<>() {
          @Override
          public DirectBuffer copy(final DirectBuffer value) {
            return Objects.requireNonNull(Decoding.copy(value));
          }

          @Override
          public void appendTo(final StringBuilder builder, final DirectBuffer value) {
            Decoding.appendString(builder, value);
          }

          @Override
          public boolean equal(final DirectBuffer a, final DirectBuffer b) {
            return Decoding.equalContent(a, b);
          }

          @Override
          public int hash(final DirectBuffer value) {
            return Decoding.contentHash(value);
          }
        };

    /** A deep copy, sharing no state with {@code value}. */
    T copy(T value);

    void appendTo(StringBuilder builder, T value);

    boolean equal(T a, T b);

    int hash(T value);
  }

  public static <T> int write(
      final MsgPackWriter writer,
      final List<?> list,
      final int depth,
      final ElementCodec<T> codec) {
    int n = writer.writeArrayHeader(list.size());
    for (final Object element : list) {
      n +=
          depth == 1
              ? codec.write(writer, cast(element))
              : write(writer, (List<?>) element, depth - 1, codec);
    }
    return n;
  }

  public static <T> int length(final List<?> list, final int depth, final ElementCodec<T> codec) {
    int n = MsgPackWriter.getEncodedArrayHeaderLength(list.size());
    for (final Object element : list) {
      n += depth == 1 ? codec.length(cast(element)) : length((List<?>) element, depth - 1, codec);
    }
    return n;
  }

  /** Replaces the content of {@code target} with the array at the reader's offset. */
  public static <T> void read(
      final MsgPackReader reader,
      final List<?> target,
      final int depth,
      final ElementCodec<T> codec) {
    final var list = mutable(target);
    list.clear();
    final int size = reader.readArrayHeader();
    for (int i = 0; i < size; i++) {
      if (depth == 1) {
        list.add(codec.read(reader));
      } else {
        final var nested = new ArrayList<Object>();
        read(reader, nested, depth - 1, codec);
        list.add(nested);
      }
    }
  }

  /** Replaces the content of {@code target} with a deep copy of {@code source}. */
  public static <T> void copy(
      final List<?> source, final List<?> target, final int depth, final ElementOps<T> ops) {
    final var list = mutable(target);
    list.clear();
    for (final Object element : source) {
      if (depth == 1) {
        list.add(ops.copy(cast(element)));
      } else {
        final var nested = new ArrayList<Object>();
        copy((List<?>) element, nested, depth - 1, ops);
        list.add(nested);
      }
    }
  }

  public static <T> void appendTo(
      final StringBuilder builder, final List<?> list, final int depth, final ElementOps<T> ops) {
    builder.append('[');
    boolean first = true;
    for (final Object element : list) {
      if (!first) {
        builder.append(',');
      }
      first = false;
      if (depth == 1) {
        ops.appendTo(builder, cast(element));
      } else {
        appendTo(builder, (List<?>) element, depth - 1, ops);
      }
    }
    builder.append(']');
  }

  public static <T> boolean equal(
      final List<?> a, final List<?> b, final int depth, final ElementOps<T> ops) {
    if (a.size() != b.size()) {
      return false;
    }
    for (int i = 0; i < a.size(); i++) {
      final boolean same =
          depth == 1
              ? ops.equal(cast(a.get(i)), cast(b.get(i)))
              : equal((List<?>) a.get(i), (List<?>) b.get(i), depth - 1, ops);
      if (!same) {
        return false;
      }
    }
    return true;
  }

  public static <T> int hash(final List<?> list, final int depth, final ElementOps<T> ops) {
    int hash = 1;
    for (final Object element : list) {
      hash =
          31 * hash
              + (depth == 1 ? ops.hash(cast(element)) : hash((List<?>) element, depth - 1, ops));
    }
    return hash;
  }

  @SuppressWarnings("unchecked")
  private static <T> T cast(final Object element) {
    return (T) element;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> mutable(final List<?> list) {
    return (List<Object>) list;
  }
}
