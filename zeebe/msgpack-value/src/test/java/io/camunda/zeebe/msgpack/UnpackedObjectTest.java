/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack;

import static io.camunda.zeebe.msgpack.MsgPackUtil.encodeMsgPack;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

import io.camunda.zeebe.msgpack.POJO.POJOEnum;
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
import io.camunda.zeebe.msgpack.value.IntegerValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class UnpackedObjectTest {

  @Nested
  class GreyBox {

    @Test
    void shouldResetObjectBeforeReadingValue() {
      // given
      final var property = new StringProperty("property", "default");
      final var unpackedObject = new UnpackedObject(1);

      unpackedObject.declareProperty(property);

      final var buffer = new UnsafeBuffer(ByteBuffer.allocate(100));

      unpackedObject.write(buffer, 0);

      final var spyUnpackedObject = spy(unpackedObject);

      // when
      spyUnpackedObject.wrap(buffer);

      // then
      final var orderOfInvocations = Mockito.inOrder(spyUnpackedObject);
      orderOfInvocations.verify(spyUnpackedObject).reset();
      orderOfInvocations.verify(spyUnpackedObject).read(Mockito.any());
    }
  }

  @Nested
  class SchemaEvolution {
    private final StringProperty sharedProperty = new StringProperty("shared", "default");

    private final UnpackedObject oldSchemaObject = new UnpackedObject(2);
    private final IntegerProperty removedProperty = new IntegerProperty("removedProperty", 42);

    private final UnpackedObject newSchemaObject = new UnpackedObject(2);
    private final BooleanProperty addedProperty = new BooleanProperty("addedProperty", false);

    private final MutableDirectBuffer bufferSerializedWithOldSchema =
        new UnsafeBuffer(ByteBuffer.allocate(100));

    private final MutableDirectBuffer bufferSerializedWithNewSchema =
        new UnsafeBuffer(ByteBuffer.allocate(100));

    {
      oldSchemaObject.declareProperty(sharedProperty);
      oldSchemaObject.declareProperty(removedProperty);

      newSchemaObject.declareProperty(sharedProperty);
      newSchemaObject.declareProperty(addedProperty);

      oldSchemaObject.write(bufferSerializedWithOldSchema, 0);
    }

    @Test
    void newPropertiesShouldHaveDefaultValueAfterReadingOldSerialization() {
      // given

      // set the new property to a value that is different from the default value
      addedProperty.setValue(true);

      // when
      newSchemaObject.wrap(bufferSerializedWithOldSchema);

      // then
      assertThat(addedProperty.getValue())
          .describedAs("value of added property after reading")
          .isFalse();
    }

    @Test
    void oldVersionCanReadDataSerializedInNewSchemaAndProvideDefaultValues() {
      // given
      sharedProperty.setValue("updated");
      addedProperty.setValue(true);
      newSchemaObject.write(bufferSerializedWithNewSchema, 0);

      // when
      oldSchemaObject.wrap(bufferSerializedWithNewSchema);

      // then
      assertThat(removedProperty.getValue())
          .describedAs("value of removed property after reading")
          .isEqualTo(42);
      assertThat(BufferUtil.bufferAsString(sharedProperty.getValue())).isEqualTo("updated");
    }

    @Test
    /* Motivated by https://github.com/camunda/camunda/pull/7143 */
    void shouldNotAccumulateSizeWithUndeclaredProperties() {

      // given
      newSchemaObject.wrap(bufferSerializedWithOldSchema);
      final int length = newSchemaObject.getLength();

      final var buffer = new UnsafeBuffer(ByteBuffer.allocate(100));
      newSchemaObject.write(buffer, 0);

      // when
      newSchemaObject.wrap(buffer);

      // then
      assertThat(newSchemaObject.getLength()).isEqualTo(length);
    }
  }

  @Nested
  class SanitizationTest {

    /**
     * Test to ensure that all property types are covered by the sanitization feature, in the off
     * chance we ever overrode toString() in a property and forgot to include the sanitization
     * logic.
     */
    @Test
    void shouldSanitizePojoWithAllProperties() {
      // given
      final TestSanitizedPojo pojo = new TestSanitizedPojo();

      // when
      final String result = pojo.toString();

      // then
      assertThat(result)
          .isEqualTo(
              """
          {"arrayProp":"***","binaryProp":"***","booleanProp":"***","documentProp":"***","enumProp":"***",\
          "intProp":"***","longProp":"***","objectProp":"***","packedProp":"***","stringProp":"***"}""");
    }

    @Test
    void shouldNotSanitizeValuesWhenSerializingToJson() {
      // given
      final TestSanitizedPojo pojo = new TestSanitizedPojo();

      // when
      final var sb = new StringBuilder();
      pojo.writeJSON(sb);
      final var result = sb.toString();

      // then
      assertThat(result)
          .isEqualTo(
              """
          {"arrayProp":[1],"binaryProp":"Zm9v","booleanProp":true,"documentProp":"gaNmb28C","enumProp":"BAR",\
          "intProp":42,"longProp":4242,"objectProp":{"foo":243},"packedProp":[packed value (length=12)],"stringProp":"string-value"}""");
    }
  }

  private static final class TestSanitizedPojo extends UnpackedObject {
    private final ArrayProperty<IntegerValue> arrayProp =
        new ArrayProperty<>("arrayProp", IntegerValue::new).sanitized();
    private final BinaryProperty binaryProp = new BinaryProperty("binaryProp").sanitized();
    private final BooleanProperty booleanProp = new BooleanProperty("booleanProp").sanitized();
    private final DocumentProperty documentProp = new DocumentProperty("documentProp").sanitized();
    private final EnumProperty<POJOEnum> enumProp =
        ((EnumProperty<POJOEnum>) new EnumProperty("enumProp", POJOEnum.class)).sanitized();
    private final IntegerProperty intProp = new IntegerProperty("intProp").sanitized();
    private final LongProperty longProp = new LongProperty("longProp").sanitized();
    private final ObjectProperty<POJONested> objectProp =
        new ObjectProperty<>("objectProp", new POJONested()).sanitized();
    private final PackedProperty packedProp = new PackedProperty("packedProp").sanitized();
    private final StringProperty stringProp = new StringProperty("stringProp").sanitized();

    public TestSanitizedPojo() {
      super(11);
      declareProperty(arrayProp)
          .declareProperty(binaryProp)
          .declareProperty(booleanProp)
          .declareProperty(documentProp)
          .declareProperty(enumProp)
          .declareProperty(intProp)
          .declareProperty(longProp)
          .declareProperty(objectProp)
          .declareProperty(packedProp)
          .declareProperty(stringProp);

      final DirectBuffer documentBytes =
          encodeMsgPack(
              (w) -> {
                w.writeMapHeader(1);
                w.writeString(wrapString("foo"));
                w.writeInteger(2);
              });

      arrayProp.add().setValue(1);
      binaryProp.setValue(BufferUtil.wrapString("foo"));
      booleanProp.setValue(true);
      documentProp.setValue(documentBytes);
      enumProp.setValue(POJOEnum.BAR);
      intProp.setValue(42);
      longProp.setValue(4242L);
      objectProp.getValue().setLong(243);
      packedProp.setValue(BufferUtil.wrapString("packed-value"), 0, 12);
      stringProp.setValue("string-value");
    }
  }
}
