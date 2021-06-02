/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.msgpack;

import static io.camunda.zeebe.msgpack.MsgPackUtil.encodeMsgPack;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public final class DocumentPropertyTest {
  @Test
  public void shouldSerializeWithDefaultValue() {
    // given
    final Document document = new Document();
    final int writeLength = document.getLength();

    // when
    final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[writeLength]);
    document.write(resultBuffer, 0);

    // then
    final Map<String, Object> msgPackMap = MsgPackUtil.asMap(resultBuffer);
    assertThat(msgPackMap).hasSize(1);
    assertThat(msgPackMap).containsExactly(entry("documentProp", MsgPackHelper.EMTPY_OBJECT));
  }

  @Test
  public void shouldThrowExceptionIfInvalidDocumentIsSet() {
    // given
    final Document document = new Document();
    final UnsafeBuffer documentBytes = new UnsafeBuffer("Hallo".getBytes());

    // when
    final Throwable throwable = catchThrowable(() -> document.setDocument(documentBytes));

    // then
    assertThat(throwable).isExactlyInstanceOf(MsgpackPropertyException.class);
    assertThat(throwable)
        .hasMessageContaining("Expected document to be a root level object, but was 'INTEGER'");
  }

  @Test
  public void shouldSerializeValue() {
    // given
    final Document document = new Document();
    final DirectBuffer documentBytes =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);
              w.writeString(wrapString("foo"));
              w.writeInteger(2);
            });
    document.setDocument(documentBytes);
    final int writeLength = document.getLength();

    // when
    final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[writeLength]);
    document.write(resultBuffer, 0);

    // then
    final byte[] bytes = new byte[documentBytes.capacity()];
    documentBytes.getBytes(0, bytes);

    final Map<String, Object> msgPackMap = MsgPackUtil.asMap(resultBuffer);
    assertThat(msgPackMap).hasSize(1);
    assertThat(msgPackMap).containsExactly(entry("documentProp", bytes));
  }

  @Test
  public void shouldSerializeEmptyEvenIfNilIsSetAsValue() {
    // given
    final Document document = new Document();
    final UnsafeBuffer documentBytes = new UnsafeBuffer(MsgPackHelper.NIL);
    document.setDocument(documentBytes);
    final int writeLength = document.getLength();

    // when
    final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[writeLength]);
    document.write(resultBuffer, 0);

    // then
    final Map<String, Object> msgPackMap = MsgPackUtil.asMap(resultBuffer);
    assertThat(msgPackMap).hasSize(1);
    assertThat(msgPackMap).containsExactly(entry("documentProp", MsgPackHelper.EMTPY_OBJECT));
  }

  @Test
  public void shouldSerializeEmptyEvenIfZeroArraysIsSetAsValue() {
    // given
    final Document document = new Document();
    final UnsafeBuffer documentBytes = new UnsafeBuffer(new byte[0]);
    document.setDocument(documentBytes);
    final int writeLength = document.getLength();

    // when
    final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[writeLength]);
    document.write(resultBuffer, 0);

    // then
    final Map<String, Object> msgPackMap = MsgPackUtil.asMap(resultBuffer);
    assertThat(msgPackMap).hasSize(1);
    assertThat(msgPackMap).containsExactly(entry("documentProp", MsgPackHelper.EMTPY_OBJECT));
  }

  @Test
  public void shouldDeserializeWithDefaultValueIfByteArrayHasZeroLength() {
    // given
    final Document document = new Document();

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);
              w.writeString(wrapString("documentProp"));
              w.writeBinary(new UnsafeBuffer(new byte[0]));
            });

    // when
    document.wrap(buffer);

    // then
    assertThat(document.getDocument().byteArray()).isEqualTo(MsgPackHelper.EMTPY_OBJECT);
  }

  @Test
  public void shouldDeserializeWithDefaultValueIfValueIsNil() {
    // given
    final Document document = new Document();

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);
              w.writeString(wrapString("documentProp"));
              w.writeBinary(new UnsafeBuffer(MsgPackHelper.NIL));
            });

    // when
    document.wrap(buffer);

    // then
    assertThat(document.getDocument().byteArray()).isEqualTo(MsgPackHelper.EMTPY_OBJECT);
  }

  @Test
  public void shouldThrowExceptionOnDeserializeInvalidDocument() {
    // given
    final Document document = new Document();
    final UnsafeBuffer documentBytes = new UnsafeBuffer("Hallo".getBytes());

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);
              w.writeString(wrapString("documentProp"));
              w.writeBinary(documentBytes);
            });

    // when
    final Throwable throwable = catchThrowable(() -> document.wrap(buffer));

    // then
    assertThat(throwable).isExactlyInstanceOf(RuntimeException.class);
    assertThat(throwable)
        .hasMessageContaining("Could not deserialize object [Document]. Deserialization stuck");

    final Throwable cause = throwable.getCause();
    assertThat(cause).isExactlyInstanceOf(RuntimeException.class);
    assertThat(cause).hasMessageContaining("Could not read property 'documentProp'");

    final Throwable rootCause = cause.getCause();
    assertThat(rootCause).isExactlyInstanceOf(IllegalArgumentException.class);
    assertThat(rootCause)
        .hasMessageContaining("Expected document to be a root level object, but was 'INTEGER'");
  }

  @Test
  public void shouldDeserializeDocument() {
    // given
    final Document document = new Document();
    final DirectBuffer documentBytes =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);
              w.writeString(wrapString("foo"));
              w.writeInteger(2);
            });

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);
              w.writeString(wrapString("documentProp"));
              w.writeBinary(documentBytes);
            });

    // when
    document.wrap(buffer);

    // then
    final DirectBuffer deserializedDocument = document.getDocument();
    final byte[] deserializedBytes = new byte[documentBytes.capacity()];
    deserializedDocument.getBytes(0, deserializedBytes);

    final byte[] bytes = new byte[documentBytes.capacity()];
    documentBytes.getBytes(0, bytes);

    assertThat(deserializedBytes).isEqualTo(bytes);
  }

  private class Document extends UnpackedObject {
    private final DocumentProperty documentProperty = new DocumentProperty("documentProp");

    Document() {
      declareProperty(documentProperty);
    }

    public DirectBuffer getDocument() {
      return documentProperty.getValue();
    }

    public void setDocument(final DirectBuffer document) {
      documentProperty.setValue(document);
    }
  }
}
