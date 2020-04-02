/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.primitive.service.impl;

import io.atomix.primitive.service.BackupInput;
import io.atomix.storage.buffer.Buffer;
import io.atomix.storage.buffer.BufferInput;
import io.atomix.storage.buffer.Bytes;
import io.atomix.utils.serializer.Serializer;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/** Default backup input. */
public class DefaultBackupInput implements BackupInput {
  private final BufferInput<?> input;
  private final Serializer serializer;

  public DefaultBackupInput(final BufferInput<?> input, final Serializer serializer) {
    this.input = input;
    this.serializer = serializer;
  }

  @Override
  public <U> U readObject() {
    return input.readObject(bytes -> bytes != null ? serializer.decode(bytes) : null);
  }

  @Override
  public int position() {
    return input.position();
  }

  @Override
  public int remaining() {
    return input.remaining();
  }

  @Override
  public boolean hasRemaining() {
    return input.hasRemaining();
  }

  @Override
  public BackupInput skip(final int bytes) {
    input.skip(bytes);
    return this;
  }

  @Override
  public BackupInput read(final Bytes bytes) {
    input.read(bytes);
    return this;
  }

  @Override
  public BackupInput read(final byte[] bytes) {
    input.read(bytes);
    return this;
  }

  @Override
  public BackupInput read(final Bytes bytes, final int offset, final int length) {
    input.read(bytes, offset, length);
    return this;
  }

  @Override
  public BackupInput read(final byte[] bytes, final int offset, final int length) {
    input.read(bytes, offset, length);
    return this;
  }

  @Override
  public BackupInput read(final Buffer buffer) {
    input.read(buffer);
    return this;
  }

  @Override
  public BackupInput read(final ByteBuffer buffer) {
    input.read(buffer);
    return this;
  }

  @Override
  public int readByte() {
    return input.readByte();
  }

  @Override
  public int readUnsignedByte() {
    return input.readUnsignedByte();
  }

  @Override
  public char readChar() {
    return input.readChar();
  }

  @Override
  public short readShort() {
    return input.readShort();
  }

  @Override
  public int readUnsignedShort() {
    return input.readUnsignedShort();
  }

  @Override
  public int readMedium() {
    return input.readMedium();
  }

  @Override
  public int readUnsignedMedium() {
    return input.readUnsignedMedium();
  }

  @Override
  public int readInt() {
    return input.readInt();
  }

  @Override
  public long readUnsignedInt() {
    return input.readUnsignedInt();
  }

  @Override
  public long readLong() {
    return input.readLong();
  }

  @Override
  public float readFloat() {
    return input.readFloat();
  }

  @Override
  public double readDouble() {
    return input.readDouble();
  }

  @Override
  public boolean readBoolean() {
    return input.readBoolean();
  }

  @Override
  public String readString() {
    return input.readString();
  }

  @Override
  public String readString(final Charset charset) {
    return input.readString(charset);
  }

  @Override
  public String readUTF8() {
    return input.readUTF8();
  }

  @Override
  public void close() {
    input.close();
  }
}
