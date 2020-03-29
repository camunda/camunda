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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;

/**
 * Base buffer test.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public abstract class BufferTest {

  /** Creates a new test buffer. */
  protected abstract Buffer createBuffer(int capacity);

  /** Creates a new test buffer. */
  protected abstract Buffer createBuffer(int capacity, int maxCapacity);

  @Test
  public void testPosition() {
    final Buffer buffer = createBuffer(8);
    assertEquals(0, buffer.position());
    buffer.writeInt(10);
    assertEquals(4, buffer.position());
    buffer.position(0);
    assertEquals(0, buffer.position());
    assertEquals(10, buffer.readInt());
  }

  @Test
  public void testFlip() {
    final Buffer buffer = createBuffer(8);
    buffer.writeInt(10);
    assertEquals(4, buffer.position());
    assertEquals(8, buffer.capacity());
    assertEquals(-1, buffer.limit());
    assertEquals(8, buffer.capacity());
    buffer.flip();
    assertEquals(4, buffer.limit());
    assertEquals(0, buffer.position());
  }

  @Test
  public void testLimit() {
    final Buffer buffer = createBuffer(8);
    assertEquals(0, buffer.position());
    assertEquals(-1, buffer.limit());
    assertEquals(8, buffer.capacity());
    buffer.limit(4);
    assertEquals(4, buffer.limit());
    assertTrue(buffer.hasRemaining());
    buffer.writeInt(10);
    assertEquals(0, buffer.remaining());
    assertFalse(buffer.hasRemaining());
  }

  @Test
  public void testClear() {
    final Buffer buffer = createBuffer(8);
    buffer.limit(6);
    assertEquals(6, buffer.limit());
    buffer.writeInt(10);
    assertEquals(4, buffer.position());
    buffer.clear();
    assertEquals(-1, buffer.limit());
    assertEquals(8, buffer.capacity());
    assertEquals(0, buffer.position());
  }

  @Test
  public void testMarkReset() {
    assertTrue(createBuffer(12).writeInt(10).mark().writeBoolean(true).reset().readBoolean());
  }

  @Test(expected = BufferUnderflowException.class)
  public void testReadIntThrowsBufferUnderflowWithNoRemainingBytesRelative() {
    createBuffer(4, 4).writeInt(10).readInt();
  }

  @Test(expected = BufferUnderflowException.class)
  public void testReadIntThrowsBufferUnderflowWithNoRemainingBytesAbsolute() {
    createBuffer(4, 4).readInt(2);
  }

  @Test(expected = BufferOverflowException.class)
  public void testWriteIntThrowsBufferOverflowWithNoRemainingBytesRelative() {
    createBuffer(4, 4).writeInt(10).writeInt(20);
  }

  @Test(expected = BufferOverflowException.class)
  public void testReadIntThrowsBufferOverflowWithNoRemainingBytesAbsolute() {
    createBuffer(4, 4).writeInt(4, 10);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void testReadIntThrowsIndexOutOfBounds() {
    createBuffer(4, 4).readInt(10);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void testWriteIntThrowsIndexOutOfBounds() {
    createBuffer(4, 4).writeInt(10, 10);
  }

  @Test
  public void testWriteReadByteRelative() {
    assertEquals(10, createBuffer(16).writeByte(10).flip().readByte());
  }

  @Test
  public void testWriteReadByteAbsolute() {
    assertEquals(10, createBuffer(16).writeByte(4, 10).readByte(4));
  }

  @Test
  public void testWriteReadUnsignedByteRelative() {
    assertEquals(10, createBuffer(16).writeUnsignedByte(10).flip().readUnsignedByte());
  }

  @Test
  public void testWriteReadUnsignedByteAbsolute() {
    assertEquals(10, createBuffer(16).writeUnsignedByte(4, 10).readUnsignedByte(4));
  }

  @Test
  public void testWriteReadShortRelative() {
    assertEquals(10, createBuffer(16).writeShort((short) 10).flip().readShort());
  }

  @Test
  public void testWriteReadShortAbsolute() {
    assertEquals(10, createBuffer(16).writeShort(4, (short) 10).readShort(4));
  }

  @Test
  public void testWriteReadUnsignedShortRelative() {
    assertEquals(10, createBuffer(16).writeUnsignedShort((short) 10).flip().readUnsignedShort());
  }

  @Test
  public void testWriteReadUnsignedShortAbsolute() {
    assertEquals(10, createBuffer(16).writeUnsignedShort(4, (short) 10).readUnsignedShort(4));
  }

  @Test
  public void testWriteReadIntRelative() {
    assertEquals(10, createBuffer(16).writeInt(10).flip().readInt());
  }

  @Test
  public void testWriteReadUnsignedIntAbsolute() {
    assertEquals(10, createBuffer(16).writeUnsignedInt(4, 10).readUnsignedInt(4));
  }

  @Test
  public void testWriteReadUnsignedIntRelative() {
    assertEquals(10, createBuffer(16).writeUnsignedInt(10).flip().readUnsignedInt());
  }

  @Test
  public void testWriteReadIntAbsolute() {
    assertEquals(10, createBuffer(16).writeInt(4, 10).readInt(4));
  }

  @Test
  public void testWriteReadLongRelative() {
    assertEquals(12345, createBuffer(16).writeLong(12345).flip().readLong());
  }

  @Test
  public void testWriteReadLongAbsolute() {
    assertEquals(12345, createBuffer(16).writeLong(4, 12345).readLong(4));
  }

  @Test
  public void testWriteReadFloatRelative() {
    assertEquals(10.6f, createBuffer(16).writeFloat(10.6f).flip().readFloat(), .001);
  }

  @Test
  public void testWriteReadFloatAbsolute() {
    assertEquals(10.6f, createBuffer(16).writeFloat(4, 10.6f).readFloat(4), .001);
  }

  @Test
  public void testWriteReadDoubleRelative() {
    assertEquals(10.6, createBuffer(16).writeDouble(10.6).flip().readDouble(), .001);
  }

  @Test
  public void testWriteReadDoubleAbsolute() {
    assertEquals(10.6, createBuffer(16).writeDouble(4, 10.6).readDouble(4), .001);
  }

  @Test
  public void testWriteReadBooleanRelative() {
    assertTrue(createBuffer(16).writeBoolean(true).flip().readBoolean());
  }

  @Test
  public void testWriteReadBooleanAbsolute() {
    assertTrue(createBuffer(16).writeBoolean(4, true).readBoolean(4));
  }

  @Test
  public void testWriteReadStringRelative() {
    final Buffer buffer =
        createBuffer(38).writeString("Hello world!").writeString("Hello world again!").flip();
    assertEquals("Hello world!", buffer.readString());
    assertEquals("Hello world again!", buffer.readString());
  }

  @Test
  public void testWriteReadStringAbsolute() {
    final Buffer buffer =
        createBuffer(46).writeString(4, "Hello world!").writeString(20, "Hello world again!");
    assertEquals("Hello world!", buffer.readString(4));
    assertEquals("Hello world again!", buffer.readString(20));
  }

  @Test
  public void testWriteReadUTF8Relative() {
    final Buffer buffer =
        createBuffer(38).writeUTF8("Hello world!").writeUTF8("Hello world again!").flip();
    assertEquals("Hello world!", buffer.readUTF8());
    assertEquals("Hello world again!", buffer.readUTF8());
  }

  @Test
  public void testWriteReadUTF8Absolute() {
    final Buffer buffer =
        createBuffer(46).writeUTF8(4, "Hello world!").writeUTF8(20, "Hello world again!");
    assertEquals("Hello world!", buffer.readUTF8(4));
    assertEquals("Hello world again!", buffer.readUTF8(20));
  }

  @Test
  public void testReadWriter() {
    final Buffer writeBuffer = createBuffer(8).writeLong(10).flip();
    final Buffer readBuffer = createBuffer(8);
    writeBuffer.read(readBuffer);
    assertEquals(10, readBuffer.flip().readLong());
  }

  @Test
  public void testWriteReadSwappedIntRelative() {
    assertEquals(10, createBuffer(16).order(ByteOrder.LITTLE_ENDIAN).writeInt(10).flip().readInt());
  }

  @Test
  public void testWriteReadSwappedIntAbsolute() {
    assertEquals(10, createBuffer(16).order(ByteOrder.LITTLE_ENDIAN).writeInt(4, 10).readInt(4));
  }

  @Test
  public void testAbsoluteSlice() {
    final Buffer buffer = createBuffer(1024);
    buffer.writeLong(10).writeLong(11).rewind();
    final Buffer slice = buffer.slice(8, 1016);
    assertEquals(0, slice.position());
    assertEquals(11, slice.readLong());
  }

  @Test
  public void testRelativeSliceWithoutLength() {
    final Buffer buffer = createBuffer(1024, 1024);
    buffer.writeLong(10).writeLong(11).writeLong(12).rewind();
    assertEquals(10, buffer.readLong());
    final Buffer slice = buffer.slice();
    assertEquals(0, slice.position());
    assertEquals(-1, slice.limit());
    assertEquals(1016, slice.capacity());
    assertEquals(1016, slice.maxCapacity());
    assertEquals(11, slice.readLong());
    assertEquals(11, slice.readLong(0));
    slice.close();
    final Buffer slice2 = buffer.skip(8).slice();
    assertEquals(0, slice2.position());
    assertEquals(-1, slice2.limit());
    assertEquals(1008, slice2.capacity());
    assertEquals(1008, slice2.maxCapacity());
    assertEquals(12, slice2.readLong());
    assertEquals(12, slice2.readLong(0));
  }

  @Test
  public void testRelativeSliceWithLength() {
    final Buffer buffer = createBuffer(1024);
    buffer.writeLong(10).writeLong(11).writeLong(12).rewind();
    assertEquals(10, buffer.readLong());
    final Buffer slice = buffer.slice(8);
    assertEquals(0, slice.position());
    assertEquals(11, slice.readLong());
    assertEquals(11, slice.readLong(0));
    slice.close();
    final Buffer slice2 = buffer.skip(8).slice(8);
    assertEquals(0, slice2.position());
    assertEquals(12, slice2.readLong());
    assertEquals(12, slice2.readLong(0));
    slice2.close();
  }

  @Test
  public void testSliceOfSlice() {
    final Buffer buffer = createBuffer(1024);
    buffer.writeLong(10).writeLong(11).writeLong(12).rewind();
    assertEquals(10, buffer.readLong());
    final Buffer slice = buffer.slice();
    assertEquals(11, slice.readLong());
    final Buffer sliceOfSlice = slice.slice();
    assertEquals(12, sliceOfSlice.readLong());
    assertEquals(8, sliceOfSlice.position());
  }

  @Test
  public void testSliceWithLimit() {
    final Buffer buffer = createBuffer(1024).limit(16);
    buffer.writeLong(10);
    final Buffer slice = buffer.slice();
    assertEquals(0, slice.position());
    assertEquals(8, slice.capacity());
    assertEquals(8, slice.maxCapacity());
    assertEquals(8, slice.remaining());
  }

  @Test
  public void testSliceWithLittleRemaining() {
    final Buffer buffer = createBuffer(1024, 2048);
    buffer.position(1020);
    final Buffer slice = buffer.slice(8);
    assertEquals(0, slice.position());
    assertEquals(-1, slice.limit());
  }

  @Test
  public void testCompact() {
    final Buffer buffer = createBuffer(1024);
    buffer.position(100).writeLong(1234).position(100).compact();
    assertEquals(0, buffer.position());
    assertEquals(1234, buffer.readLong());
  }

  @Test
  public void testSwappedPosition() {
    final Buffer buffer = createBuffer(8).order(ByteOrder.LITTLE_ENDIAN);
    assertEquals(0, buffer.position());
    buffer.writeInt(10);
    assertEquals(4, buffer.position());
    buffer.position(0);
    assertEquals(0, buffer.position());
    assertEquals(10, buffer.readInt());
  }

  @Test
  public void testSwappedFlip() {
    final Buffer buffer = createBuffer(8).order(ByteOrder.LITTLE_ENDIAN);
    buffer.writeInt(10);
    assertEquals(4, buffer.position());
    assertEquals(8, buffer.capacity());
    assertEquals(-1, buffer.limit());
    assertEquals(8, buffer.capacity());
    buffer.flip();
    assertEquals(4, buffer.limit());
    assertEquals(0, buffer.position());
  }

  @Test
  public void testSwappedLimit() {
    final Buffer buffer = createBuffer(8).order(ByteOrder.LITTLE_ENDIAN);
    assertEquals(0, buffer.position());
    assertEquals(-1, buffer.limit());
    assertEquals(8, buffer.capacity());
    buffer.limit(4);
    assertEquals(4, buffer.limit());
    assertTrue(buffer.hasRemaining());
    buffer.writeInt(10);
    assertEquals(0, buffer.remaining());
    assertFalse(buffer.hasRemaining());
  }

  @Test
  public void testSwappedClear() {
    final Buffer buffer = createBuffer(8).order(ByteOrder.LITTLE_ENDIAN);
    buffer.limit(6);
    assertEquals(6, buffer.limit());
    buffer.writeInt(10);
    assertEquals(4, buffer.position());
    buffer.clear();
    assertEquals(-1, buffer.limit());
    assertEquals(8, buffer.capacity());
    assertEquals(0, buffer.position());
  }

  @Test
  public void testSwappedMarkReset() {
    assertTrue(
        createBuffer(12)
            .order(ByteOrder.LITTLE_ENDIAN)
            .writeInt(10)
            .mark()
            .writeBoolean(true)
            .reset()
            .readBoolean());
  }

  @Test(expected = BufferUnderflowException.class)
  public void testSwappedReadIntThrowsBufferUnderflowWithNoRemainingBytesRelative() {
    createBuffer(4, 4).order(ByteOrder.LITTLE_ENDIAN).writeInt(10).readInt();
  }

  @Test(expected = BufferUnderflowException.class)
  public void testSwappedReadIntThrowsBufferUnderflowWithNoRemainingBytesAbsolute() {
    createBuffer(4, 4).order(ByteOrder.LITTLE_ENDIAN).readInt(2);
  }

  @Test(expected = BufferOverflowException.class)
  public void testSwappedWriteIntThrowsBufferOverflowWithNoRemainingBytesRelative() {
    createBuffer(4, 4).order(ByteOrder.LITTLE_ENDIAN).writeInt(10).writeInt(20);
  }

  @Test(expected = BufferOverflowException.class)
  public void testSwappedReadIntThrowsBufferOverflowWithNoRemainingBytesAbsolute() {
    createBuffer(4, 4).order(ByteOrder.LITTLE_ENDIAN).writeInt(4, 10);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void testSwappedReadIntThrowsIndexOutOfBounds() {
    createBuffer(4, 4).order(ByteOrder.LITTLE_ENDIAN).readInt(10);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void testSwappedWriteIntThrowsIndexOutOfBounds() {
    createBuffer(4, 4).order(ByteOrder.LITTLE_ENDIAN).writeInt(10, 10);
  }

  @Test
  public void testSwappedWriteReadByteRelative() {
    assertEquals(
        10, createBuffer(16).order(ByteOrder.LITTLE_ENDIAN).writeByte(10).flip().readByte());
  }

  @Test
  public void testSwappedWriteReadByteAbsolute() {
    assertEquals(10, createBuffer(16).order(ByteOrder.LITTLE_ENDIAN).writeByte(4, 10).readByte(4));
  }

  @Test
  public void testSwappedWriteReadUnsignedByteRelative() {
    assertEquals(
        10,
        createBuffer(16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .writeUnsignedByte(10)
            .flip()
            .readUnsignedByte());
  }

  @Test
  public void testSwappedWriteReadUnsignedByteAbsolute() {
    assertEquals(
        10,
        createBuffer(16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .writeUnsignedByte(4, 10)
            .readUnsignedByte(4));
  }

  @Test
  public void testSwappedWriteReadShortRelative() {
    assertEquals(
        10,
        createBuffer(16).order(ByteOrder.LITTLE_ENDIAN).writeShort((short) 10).flip().readShort());
  }

  @Test
  public void testSwappedWriteReadShortAbsolute() {
    assertEquals(
        10, createBuffer(16).order(ByteOrder.LITTLE_ENDIAN).writeShort(4, (short) 10).readShort(4));
  }

  @Test
  public void testSwappedWriteReadUnsignedShortRelative() {
    assertEquals(
        10,
        createBuffer(16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .writeUnsignedShort((short) 10)
            .flip()
            .readUnsignedShort());
  }

  @Test
  public void testSwappedWriteReadUnsignedShortAbsolute() {
    assertEquals(
        10,
        createBuffer(16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .writeUnsignedShort(4, (short) 10)
            .readUnsignedShort(4));
  }

  @Test
  public void testSwappedWriteReadIntRelative() {
    assertEquals(10, createBuffer(16).order(ByteOrder.LITTLE_ENDIAN).writeInt(10).flip().readInt());
  }

  @Test
  public void testSwappedWriteReadUnsignedIntAbsolute() {
    assertEquals(
        10,
        createBuffer(16).order(ByteOrder.LITTLE_ENDIAN).writeUnsignedInt(4, 10).readUnsignedInt(4));
  }

  @Test
  public void testSwappedWriteReadUnsignedIntRelative() {
    assertEquals(
        10,
        createBuffer(16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .writeUnsignedInt(10)
            .flip()
            .readUnsignedInt());
  }

  @Test
  public void testSwappedWriteReadIntAbsolute() {
    assertEquals(10, createBuffer(16).order(ByteOrder.LITTLE_ENDIAN).writeInt(4, 10).readInt(4));
  }

  @Test
  public void testSwappedWriteReadLongRelative() {
    assertEquals(
        12345, createBuffer(16).order(ByteOrder.LITTLE_ENDIAN).writeLong(12345).flip().readLong());
  }

  @Test
  public void testSwappedWriteReadLongAbsolute() {
    assertEquals(
        12345, createBuffer(16).order(ByteOrder.LITTLE_ENDIAN).writeLong(4, 12345).readLong(4));
  }

  @Test
  public void testSwappedWriteReadFloatRelative() {
    assertEquals(
        10.6f,
        createBuffer(16).order(ByteOrder.LITTLE_ENDIAN).writeFloat(10.6f).flip().readFloat(),
        .001);
  }

  @Test
  public void testSwappedWriteReadFloatAbsolute() {
    assertEquals(
        10.6f,
        createBuffer(16).order(ByteOrder.LITTLE_ENDIAN).writeFloat(4, 10.6f).readFloat(4),
        .001);
  }

  @Test
  public void testSwappedWriteReadDoubleRelative() {
    assertEquals(
        10.6,
        createBuffer(16).order(ByteOrder.LITTLE_ENDIAN).writeDouble(10.6).flip().readDouble(),
        .001);
  }

  @Test
  public void testSwappedWriteReadDoubleAbsolute() {
    assertEquals(
        10.6,
        createBuffer(16).order(ByteOrder.LITTLE_ENDIAN).writeDouble(4, 10.6).readDouble(4),
        .001);
  }

  @Test
  public void testSwappedWriteReadBooleanRelative() {
    assertTrue(
        createBuffer(16).order(ByteOrder.LITTLE_ENDIAN).writeBoolean(true).flip().readBoolean());
  }

  @Test
  public void testSwappedWriteReadBooleanAbsolute() {
    assertTrue(
        createBuffer(16).order(ByteOrder.LITTLE_ENDIAN).writeBoolean(4, true).readBoolean(4));
  }

  @Test
  public void testSwappedReadWriter() {
    final Buffer writeBuffer = createBuffer(8).order(ByteOrder.LITTLE_ENDIAN).writeLong(10).flip();
    final Buffer readBuffer = createBuffer(8).order(ByteOrder.LITTLE_ENDIAN);
    writeBuffer.read(readBuffer);
    assertEquals(10, readBuffer.flip().readLong());
  }

  @Test
  public void testSwappedAbsoluteSlice() {
    final Buffer buffer = createBuffer(1024).order(ByteOrder.LITTLE_ENDIAN);
    buffer.writeLong(10).writeLong(11).rewind();
    final Buffer slice = buffer.slice(8, 1016);
    assertEquals(0, slice.position());
    assertEquals(11, slice.readLong());
  }

  @Test
  public void testSwappedRelativeSliceWithoutLength() {
    final Buffer buffer = createBuffer(1024, 1024).order(ByteOrder.LITTLE_ENDIAN);
    buffer.writeLong(10).writeLong(11).writeLong(12).rewind();
    assertEquals(10, buffer.readLong());
    final Buffer slice = buffer.slice();
    assertEquals(0, slice.position());
    assertEquals(-1, slice.limit());
    assertEquals(1016, slice.capacity());
    assertEquals(1016, slice.maxCapacity());
    assertEquals(11, slice.readLong());
    assertEquals(11, slice.readLong(0));
    slice.close();
    final Buffer slice2 = buffer.skip(8).slice();
    assertEquals(0, slice2.position());
    assertEquals(-1, slice2.limit());
    assertEquals(1008, slice2.capacity());
    assertEquals(1008, slice2.maxCapacity());
    assertEquals(12, slice2.readLong());
    assertEquals(12, slice2.readLong(0));
  }

  @Test
  public void testSwappedRelativeSliceWithLength() {
    final Buffer buffer = createBuffer(1024).order(ByteOrder.LITTLE_ENDIAN);
    buffer.writeLong(10).writeLong(11).writeLong(12).rewind();
    assertEquals(10, buffer.readLong());
    final Buffer slice = buffer.slice(8);
    assertEquals(0, slice.position());
    assertEquals(11, slice.readLong());
    assertEquals(11, slice.readLong(0));
    slice.close();
    final Buffer slice2 = buffer.skip(8).slice(8);
    assertEquals(0, slice2.position());
    assertEquals(12, slice2.readLong());
    assertEquals(12, slice2.readLong(0));
    slice2.close();
  }

  @Test
  public void testSwappedSliceOfSlice() {
    final Buffer buffer = createBuffer(1024).order(ByteOrder.LITTLE_ENDIAN);
    buffer.writeLong(10).writeLong(11).writeLong(12).rewind();
    assertEquals(10, buffer.readLong());
    final Buffer slice = buffer.slice();
    assertEquals(11, slice.readLong());
    final Buffer sliceOfSlice = slice.slice();
    assertEquals(12, sliceOfSlice.readLong());
    assertEquals(8, sliceOfSlice.position());
  }

  @Test
  public void testSwappedSliceWithLimit() {
    final Buffer buffer = createBuffer(1024).order(ByteOrder.LITTLE_ENDIAN).limit(16);
    buffer.writeLong(10);
    final Buffer slice = buffer.slice();
    assertEquals(0, slice.position());
    assertEquals(8, slice.capacity());
    assertEquals(8, slice.maxCapacity());
    assertEquals(8, slice.remaining());
  }

  @Test
  public void testSwappedSliceWithLittleRemaining() {
    final Buffer buffer = createBuffer(1024, 2048).order(ByteOrder.LITTLE_ENDIAN);
    buffer.position(1020);
    final Buffer slice = buffer.slice(8);
    assertEquals(0, slice.position());
    assertEquals(-1, slice.limit());
  }

  @Test
  public void testSwappedCompact() {
    final Buffer buffer = createBuffer(1024).order(ByteOrder.LITTLE_ENDIAN);
    buffer.position(100).writeLong(1234).position(100).compact();
    assertEquals(0, buffer.position());
    assertEquals(1234, buffer.readLong());
  }

  @Test
  public void testCapacity0Read() {
    final Buffer buffer = createBuffer(0, 1024);
    assertEquals(0, buffer.readLong());
  }

  @Test
  public void testCapacity0Write() {
    final Buffer buffer = createBuffer(0, 1024);
    buffer.writeLong(10);
    assertEquals(10, buffer.readLong(0));
  }

  @Test
  public void shouldWriteByteBuffer() {
    // given
    final Buffer dest = createBuffer(0, 1024);
    final ByteBuffer src = ByteBuffer.allocateDirect(2 * Long.BYTES);
    src.putLong(1L).putLong(Long.BYTES, 2L);

    // when
    dest.write(src);

    // then
    // check that we wrote that we expect
    assertEquals(2L, dest.readLong(0));
    // the destination buffer's position has been updated
    assertEquals(Long.BYTES, dest.position());
    // the source buffer's position remains unchanged
    assertEquals(Long.BYTES, src.position());
  }

  @Test(expected = BufferOverflowException.class)
  public void shouldFailByteBufferIfCapacityTooLow() {
    // given
    final Buffer dest = createBuffer(0, Integer.BYTES).limit(Integer.BYTES);
    final ByteBuffer src = ByteBuffer.allocateDirect(Long.BYTES);
    src.putLong(0, 1L);

    // when - then: will throw an exception
    dest.write(src);
  }

  @Test
  public void shouldWritePartialByteBuffer() {
    // given
    final Buffer dest = createBuffer(0, 1024).zero();
    final ByteBuffer src = ByteBuffer.allocateDirect(3 * Long.BYTES);
    src.putLong(1L).putLong(2L).putLong(3L);

    // when
    dest.write(Long.BYTES, src, Long.BYTES, 2 * Long.BYTES);

    // then
    // check that we wrote that we expect
    assertEquals(0L, dest.readLong(0));
    assertEquals(2L, dest.readLong(Long.BYTES));
    assertEquals(3L, dest.readLong(2 * Long.BYTES));

    // the destination buffer's position has NOT been updated
    assertEquals(0, dest.position());
    // the source buffer's position remains unchanged
    assertEquals(3 * Long.BYTES, src.position());
  }

  @Test(expected = BufferOverflowException.class)
  public void shouldFailPartialByteBufferIfCapacityTooLow() {
    // given
    final Buffer dest = createBuffer(0, Long.BYTES).limit(Long.BYTES);
    final ByteBuffer src = ByteBuffer.allocateDirect(3 * Long.BYTES);
    src.putLong(1L).putLong(2L).putLong(3L);

    // when - then: will throw an exception
    dest.write(Long.BYTES, src, Long.BYTES, 2 * Long.BYTES);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void shouldFailPartialByteBufferIfBufferTooSmall() {
    // given
    final Buffer dest = createBuffer(0, 2 * Long.BYTES);
    final ByteBuffer src = ByteBuffer.allocateDirect(Long.BYTES);
    src.putLong(1L);

    // when - then: will throw an exception
    dest.write(0, src, 0, 2 * Long.BYTES);
  }

  @Test
  public void shouldReadByteBuffer() {
    // given
    final Buffer src = createBuffer(0, 1024).zero();
    final ByteBuffer dest = ByteBuffer.allocateDirect(2 * Long.BYTES);
    src.writeLong(1L).writeLong(2L).writeLong(3L);
    dest.position(Long.BYTES);

    // when
    src.position(Long.BYTES).read(dest);

    // then
    // check that we wrote that we expect
    assertEquals(0L, dest.getLong(0));
    assertEquals(2L, dest.getLong(Long.BYTES));

    // the destination buffer's position has not been updated
    assertEquals(Long.BYTES, dest.position());
    // the source buffer's position has been updated by the amount of bytes read
    assertEquals(2 * Long.BYTES, src.position());
  }

  @Test
  public void shouldReadPartialByteBuffer() {
    // given
    final Buffer src = createBuffer(0, 1024).zero();
    final ByteBuffer dest = ByteBuffer.allocateDirect(2 * Long.BYTES);
    src.writeLong(1L).writeLong(2L).writeLong(3L);
    dest.position(Long.BYTES);

    // when
    src.position(Long.BYTES).read(Long.BYTES, dest, Long.BYTES, Long.BYTES);

    // then
    // check that we wrote that we expect
    assertEquals(0L, dest.getLong(0));
    assertEquals(2L, dest.getLong(Long.BYTES));

    // the destination buffer's position has not been updated
    assertEquals(Long.BYTES, dest.position());
    // the source buffer's position remains unchanged
    assertEquals(Long.BYTES, src.position());
  }
}
