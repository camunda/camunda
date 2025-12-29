/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.broker.protocol;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.PackedProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import org.jeasy.random.EasyRandom;

/** Populates implementation record values by directly setting property fields using reflection */
final class ImplRecordValuePopulator {

  private ImplRecordValuePopulator() {
    // utility class
  }

  /**
   * Populates an implementation record value by directly setting property fields with randomized
   * values. Uses setter methods for enum properties.
   *
   * @param implInstance the implementation record instance to populate
   * @param random the EasyRandom instance to use for generating random values
   */
  static void populate(final Object implInstance, final EasyRandom random) {
    final Class<?> clazz = implInstance.getClass();

    for (final Field field : clazz.getDeclaredFields()) {
      try {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        field.setAccessible(true);
        final Class<?> fieldType = field.getType();

        // Handle different msgpack property types
        if (fieldType.equals(EnumProperty.class)) {
          populateEnumProperty(implInstance, field, random);
        } else if (fieldType.equals(StringProperty.class)) {
          populateStringProperty(field, implInstance, random);
        } else if (fieldType.equals(LongProperty.class)) {
          populateLongProperty(field, implInstance, random);
        } else if (fieldType.equals(IntegerProperty.class)) {
          populateIntegerProperty(field, implInstance, random);
        } else if (fieldType.equals(BinaryProperty.class)) {
          populateBinaryProperty(field, implInstance, random);
        } else if (fieldType.equals(DocumentProperty.class)) {
          populateDocumentProperty(field, implInstance, random);
        } else if (fieldType.equals(PackedProperty.class)) {
          populatePackedProperty(field, implInstance, random);
        } else if (fieldType.equals(BooleanProperty.class)) {
          populateBooleanProperty(field, implInstance, random);
        } else if (fieldType.equals(ArrayProperty.class)) {
          populateArrayProperty(field, implInstance, random);
        } else if (fieldType.equals(ObjectProperty.class)) {
          populateObjectProperty(field, implInstance, random);
        } else if (fieldType.equals(MsgPackWriter.class) || fieldType.equals(MsgPackReader.class)) {
          // Skip other msgpack types that are more complex
        } else {
          throw new IllegalArgumentException(
              "Unsupported field type: "
                  + fieldType.getName()
                  + ", field: "
                  + field.getName()
                  + " in class: "
                  + clazz.getName());
        }
      } catch (final IllegalAccessException e) {
        throw new RuntimeException(
            "Failed to access field '" + field.getName() + "' in class '" + clazz.getName() + "'",
            e);
      }
    }
  }

  private static void populateEnumProperty(
      final Object implInstance, final Field field, final EasyRandom random)
      throws IllegalAccessException {
    // Get the enum type from the EnumProperty generic parameter
    final Type genericType = field.getGenericType();
    if (genericType instanceof final ParameterizedType paramType) {
      final Type[] typeArgs = paramType.getActualTypeArguments();
      if (typeArgs.length > 0
          && typeArgs[0] instanceof final Class<?> enumClass
          && enumClass.isEnum()) {
        final Object[] enumConstants = enumClass.getEnumConstants();
        if (enumConstants.length > 0) {
          final Object randomEnum = enumConstants[random.nextInt(enumConstants.length)];
          final EnumProperty enumProperty = (EnumProperty<?>) field.get(implInstance);
          enumProperty.setValue((Enum) randomEnum);
        }
      }
    }
  }

  private static void populateStringProperty(
      final Field field, final Object implInstance, final EasyRandom random)
      throws IllegalAccessException {
    final StringProperty stringProperty = (StringProperty) field.get(implInstance);
    if (stringProperty != null) {
      final String randomString = random.nextObject(String.class);
      if (randomString != null && !randomString.isEmpty()) {
        stringProperty.setValue(randomString);
      }
    }
  }

  private static void populateLongProperty(
      final Field field, final Object implInstance, final EasyRandom random)
      throws IllegalAccessException {
    final LongProperty longProperty = (LongProperty) field.get(implInstance);
    if (longProperty != null) {
      longProperty.setValue(random.nextLong(0, Long.MAX_VALUE));
    }
  }

  private static void populateIntegerProperty(
      final Field field, final Object implInstance, final EasyRandom random)
      throws IllegalAccessException {
    final IntegerProperty integerProperty = (IntegerProperty) field.get(implInstance);
    if (integerProperty != null) {
      // Special handling for intent properties, that are mapped to enums elsewhere
      if ("intentProperty".equals(field.getName())) {
        integerProperty.setValue(0);
      } else {
        integerProperty.setValue(random.nextInt());
      }
    }
  }

  private static void populateBinaryProperty(
      final Field field, final Object implInstance, final EasyRandom random)
      throws IllegalAccessException {
    final BinaryProperty binaryProperty = (BinaryProperty) field.get(implInstance);
    if (binaryProperty != null) {
      final String randomString = random.nextObject(String.class);
      binaryProperty.setValue(BufferUtil.wrapString(randomString));
    }
  }

  private static void populateDocumentProperty(
      final Field field, final Object implInstance, final EasyRandom random)
      throws IllegalAccessException {
    final DocumentProperty documentProperty = (DocumentProperty) field.get(implInstance);
    if (documentProperty != null) {
      // Generate a random map and encode it as msgpack document
      final Map<String, Object> randomMap = new HashMap<>();
      final int size = random.nextInt(1, 5); // 1-4 entries
      for (int i = 0; i < size; i++) {
        randomMap.put("key" + i, random.nextObject(String.class));
      }
      final byte[] msgPackBytes = MsgPackConverter.convertToMsgPack(randomMap);
      documentProperty.setValue(BufferUtil.wrapArray(msgPackBytes));
    }
  }

  private static void populatePackedProperty(
      final Field field, final Object implInstance, final EasyRandom random)
      throws IllegalAccessException {
    final PackedProperty packedProperty = (PackedProperty) field.get(implInstance);
    if (packedProperty != null) {
      final Map<String, Object> randomMap = new HashMap<>();
      final int size = random.nextInt(1, 5); // 1-4 entries
      for (int i = 0; i < size; i++) {
        randomMap.put("key" + i, random.nextObject(String.class));
      }
      final byte[] msgPackBytes = MsgPackConverter.convertToMsgPack(randomMap);
      final var directBuffer = BufferUtil.wrapArray(msgPackBytes);
      packedProperty.setValue(directBuffer, 0, directBuffer.capacity());
    }
  }

  private static void populateBooleanProperty(
      final Field field, final Object implInstance, final EasyRandom random)
      throws IllegalAccessException {
    final BooleanProperty booleanProperty = (BooleanProperty) field.get(implInstance);
    if (booleanProperty != null) {
      booleanProperty.setValue(random.nextBoolean());
    }
  }

  private static void populateObjectProperty(
      final Field field, final Object implInstance, final EasyRandom random)
      throws IllegalAccessException {
    final Type genericType = field.getGenericType();
    if (genericType instanceof final ParameterizedType paramType) {
      final Type[] typeArgs = paramType.getActualTypeArguments();
      if (typeArgs.length > 0 && typeArgs[0] instanceof final Class<?> clazz) {
        if (UnifiedRecordValue.class != typeArgs[0]) {
          final var objectProperty =
              new ObjectProperty<>(
                  ((ObjectProperty<?>) field.get(implInstance)).getKey(),
                  (ObjectValue) random.nextObject(clazz));
          field.set(implInstance, objectProperty);
        }
      }
    }
  }

  private static void populateArrayProperty(
      final Field field, final Object implInstance, final EasyRandom random)
      throws IllegalAccessException {
    final Type genericType = field.getGenericType();
    if (genericType instanceof final ParameterizedType paramType) {
      final Type[] typeArgs = paramType.getActualTypeArguments();
      if (typeArgs.length > 0 && typeArgs[0] instanceof final Class<?> clazz) {
        final var arrayProperty =
            new ArrayProperty(
                ((ArrayProperty<?>) field.get(implInstance)).getKey(),
                () -> random.nextObject(clazz));
        field.set(implInstance, arrayProperty);
        final int size = random.nextInt(1, 5); // 1-4 entries
        for (int i = 0; i < size; i++) {
          arrayProperty.add();
        }
      }
    }
  }
}
