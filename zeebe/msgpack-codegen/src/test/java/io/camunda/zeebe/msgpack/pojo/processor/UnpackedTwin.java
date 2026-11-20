/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import static io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.DEFAULT_NESTED_VALUE;
import static io.camunda.zeebe.msgpack.pojo.processor.ReferenceModel.bytes;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.FieldSpec;
import io.camunda.zeebe.msgpack.pojo.processor.RandomShapes.Schema;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BaseProperty;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.PackedProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;

/** The {@link UnpackedObject} equivalent of a generated shape, the implementation it replaces. */
final class UnpackedTwin extends UnpackedObject {

  private final Map<String, BaseProperty<?>> properties = new LinkedHashMap<>();

  UnpackedTwin(final Schema schema) {
    super(schema.fields().size());
    for (final var field : schema.fields()) {
      final var key = field.key();
      final BaseProperty<?> property =
          switch (field.type()) {
            case LONG -> new LongProperty(key, -7L);
            case INT -> new IntegerProperty(key, 5);
            case BOOLEAN -> new BooleanProperty(key, true);
            case STRING -> new StringProperty(key, "d\u0001");
            case BINARY -> new BinaryProperty(key, new UnsafeBuffer(new byte[] {'d', 1}));
            case RAW -> new PackedProperty(key, new UnsafeBuffer(new byte[] {(byte) 0xc0}));
            case DOCUMENT -> new DocumentProperty(key);
            case ENUM -> new EnumProperty<>(key, Kind.class, Kind.TWO);
            case STRING_LIST -> new ArrayProperty<>(key, StringValue::new);
            case LONG_LIST -> new ArrayProperty<>(key, LongValue::new);
            case POJO_LIST, VALUE_LIST -> new ArrayProperty<>(key, Inner::new);
            case NESTED -> new ObjectProperty<>(key, new Inner());
          };
      properties.put(field.name(), property);
      declareProperty(property);
    }
  }

  /** The field in the form of {@link ReferenceModel#comparable}. */
  Object actual(final FieldSpec field) {
    final var property = properties.get(field.name());
    return switch (field.type()) {
      case LONG -> ((LongProperty) property).getValue();
      case INT -> ((IntegerProperty) property).getValue();
      case BOOLEAN -> ((BooleanProperty) property).getValue();
      case STRING -> bytes(((StringProperty) property).getValue());
      case BINARY -> bytes(((BinaryProperty) property).getValue());
      case RAW -> bytes(((PackedProperty) property).getValue());
      case DOCUMENT -> bytes(((DocumentProperty) property).getValue());
      case ENUM -> ((EnumProperty<?>) property).getValue().toString();
      case STRING_LIST -> {
        final var values = new ArrayList<ByteBuffer>();
        ((ArrayProperty<StringValue>) property).forEach(v -> values.add(bytes(v.getValue())));
        yield values;
      }
      case LONG_LIST -> {
        final var values = new ArrayList<Long>();
        ((ArrayProperty<LongValue>) property).forEach(v -> values.add(v.getValue()));
        yield values;
      }
      case NESTED -> {
        final var inner = ((ObjectProperty<Inner>) property).getValue();
        yield List.of(inner.value.getValue(), bytes(inner.text.getValue()));
      }
      case POJO_LIST, VALUE_LIST -> {
        final var values = new ArrayList<Object>();
        ((ArrayProperty<Inner>) property)
            .forEach(v -> values.add(List.of(v.value.getValue(), bytes(v.text.getValue()))));
        yield values;
      }
    };
  }

  private static final class Inner extends UnpackedObject {
    private final IntegerProperty value = new IntegerProperty("value", DEFAULT_NESTED_VALUE);
    private final StringProperty text = new StringProperty("text", "");

    Inner() {
      super(2);
      declareProperty(value).declareProperty(text);
    }
  }

  private enum Kind {
    ONE,
    TWO,
    THREE
  }
}
