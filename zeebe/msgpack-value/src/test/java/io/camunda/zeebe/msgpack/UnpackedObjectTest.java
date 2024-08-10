/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.ByteBuffer;
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
}
