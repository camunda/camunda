/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.msgpack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import java.nio.ByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class UnpackedObjectTest {

  @Nested
  public class GreyBox {

    @Test
    public void shouldResetObjectBeforeReadingValue() {
      // given
      final var property = new StringProperty("property", "default");
      final var unpackedObject = new UnpackedObject();

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
  public class SchemaEvolution {
    private final StringProperty sharedProperty = new StringProperty("shared", "default");

    private final UnpackedObject oldSchemaObject = new UnpackedObject();
    private final IntegerProperty removedProperty = new IntegerProperty("removedProperty", 42);

    private final UnpackedObject newSchemaObject = new UnpackedObject();
    private final BooleanProperty addedProperty = new BooleanProperty("addedProperty", false);

    private final MutableDirectBuffer bufferSerializedWithOldSchema =
        new UnsafeBuffer(ByteBuffer.allocate(100));

    {
      oldSchemaObject.declareProperty(sharedProperty);
      oldSchemaObject.declareProperty(removedProperty);

      newSchemaObject.declareProperty(sharedProperty);
      newSchemaObject.declareProperty(addedProperty);

      oldSchemaObject.write(bufferSerializedWithOldSchema, 0);
    }

    @Test
    public void newPropertiesShouldHaveDefaultValueAfterReadingOldSerialization() {
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
    /* Motivated by https://github.com/camunda/zeebe/pull/7143 */
    public void shouldNotAccumulateSizeWithUndeclaredProperties() {

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
