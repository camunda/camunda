/*
 * Copyright 2015-present Open Networking Foundation
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
package io.atomix.storage.buffer;

import io.atomix.utils.concurrent.ReferenceManager;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

/**
 * Read-only buffer.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class ReadOnlyBuffer extends AbstractBuffer {
  private final Buffer root;

  public ReadOnlyBuffer(final Buffer buffer, final ReferenceManager<Buffer> referenceManager) {
    super(buffer.bytes(), referenceManager);
    this.root = buffer;
  }

  @Override
  public Buffer duplicate() {
    return new ReadOnlyBuffer(root, referenceManager);
  }

  @Override
  public Buffer acquire() {
    root.acquire();
    return this;
  }

  @Override
  public boolean release() {
    return root.release();
  }

  @Override
  public boolean isDirect() {
    return root.isDirect();
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public boolean isFile() {
    return root.isFile();
  }

  @Override
  public void close() {
    root.release();
  }

  @Override
  public Buffer compact() {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer write(final Bytes bytes) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer write(final byte[] bytes) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer write(final ByteBuffer src) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer write(final Bytes bytes, final int offset, final int length) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer write(final byte[] bytes, final int offset, final int length) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer write(final Buffer buffer) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeByte(final int b) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeUnsignedByte(final int b) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeChar(final char c) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeShort(final short s) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeUnsignedShort(final int s) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeInt(final int i) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeUnsignedInt(final long i) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeLong(final long l) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeFloat(final float f) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeDouble(final double d) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeBoolean(final boolean b) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeUTF8(final String s) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer write(final int offset, final Bytes bytes, final int srcOffset, final int length) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer write(final int offset, final byte[] bytes, final int srcOffset, final int length) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer write(
      final int offset, final ByteBuffer src, final int srcOffset, final int length) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeByte(final int offset, final int b) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeUnsignedByte(final int offset, final int b) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeChar(final int offset, final char c) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeShort(final int offset, final short s) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeUnsignedShort(final int offset, final int s) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeInt(final int offset, final int i) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeUnsignedInt(final int offset, final long i) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeLong(final int offset, final long l) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeFloat(final int offset, final float f) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeDouble(final int offset, final double d) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeBoolean(final int offset, final boolean b) {
    throw new ReadOnlyBufferException();
  }

  @Override
  protected void compact(final int from, final int to, final int length) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer zero() {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer zero(final int offset) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer zero(final int offset, final int length) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeMedium(final int offset, final int m) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeUnsignedMedium(final int offset, final int m) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer flush() {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeMedium(final int m) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public Buffer writeUnsignedMedium(final int m) {
    throw new ReadOnlyBufferException();
  }
}
