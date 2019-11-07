package io.zeebe.logstreams.storage.memory;

import io.zeebe.logstreams.impl.LoggedEventImpl;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.LogStorageReader;
import io.zeebe.logstreams.spi.ReadResultProcessor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.LongConsumer;
import java.util.function.LongUnaryOperator;
import org.agrona.concurrent.UnsafeBuffer;

public class MemoryLogStorage implements LogStorage, LogStorageReader {
  private final LoggedEventImpl positionReader = new LoggedEventImpl();

  private ByteBuffer buffer;
  private NavigableMap<Long, Integer> positions;
  private LongConsumer commitListener;

  private volatile boolean opened;

  public MemoryLogStorage(final int capacity) {
    this(ByteBuffer.allocate(capacity));
  }

  public MemoryLogStorage(final ByteBuffer buffer) {
    this.buffer = buffer;
    this.positions = new TreeMap<>();
  }

  public synchronized void setCommitListener(final LongConsumer commitListener) {
    this.commitListener = commitListener;
  }

  @Override
  public LogStorageReader newReader() {
    return this;
  }

  @Override
  public synchronized long append(final ByteBuffer blockBuffer) {
    final var view = new UnsafeBuffer(blockBuffer);
    final var address = buffer.position();
    var offset = 0;

    // iterate over all events, copy them to the buffer and record the position -> address mapping
    do {
      positionReader.wrap(view, offset);
      final var length = positionReader.getLength();
      view.getBytes(offset, buffer, buffer.position(), length);
      positions.put(positionReader.getPosition(), buffer.position());
      buffer.position(buffer.position() + length);
      offset += length;
    } while (offset < view.capacity());

    if (commitListener != null) {
      commitListener.accept(positions.lastEntry().getKey());
    }

    return address;
  }

  @Override
  public synchronized void delete(final long address) {
    if (address > buffer.position() || address < 0) {
      return;
    }

    // perform a copy of the remaining bytes
    buffer = ByteBuffer.allocate(buffer.capacity()).put(buffer.position((int) address));
    positions = positions.headMap(address, true);
  }

  @Override
  public void open() throws IOException {
    opened = true;
  }

  @Override
  public synchronized void close() {
    opened = false;
  }

  @Override
  public boolean isOpen() {
    return opened;
  }

  @Override
  public boolean isClosed() {
    return !opened;
  }

  @Override
  public void flush() throws Exception {
    // do nothing
  }

  @Override
  public long getFirstBlockAddress() {
    return 0;
  }

  @Override
  public synchronized long read(final ByteBuffer readBuffer, final long address) {
    return read(readBuffer, address, (ignored, length) -> length);
  }

  @Override
  public synchronized long read(
      final ByteBuffer readBuffer, final long address, final ReadResultProcessor processor) {
    if (buffer.position() == 0) {
      return LogStorage.OP_RESULT_NO_DATA;
    }

    if (address > buffer.position() || address < 0) {
      return LogStorage.OP_RESULT_INVALID_ADDR;
    }

    final var view = buffer.slice().position((int) address);
    final var length = view.remaining();

    if (readBuffer.remaining() < length) {
      return LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;
    }

    readBuffer.put(view);
    final var result = processor.process(readBuffer, length);
    if (result < 0) {
      return result;
    }

    return address + result;
  }

  @Override
  public synchronized long readLastBlock(
      final ByteBuffer readBuffer, final ReadResultProcessor processor) {
    final var lastAddress = positions.isEmpty() ? -1 : positions.lastEntry().getValue();
    if (lastAddress < 0) {
      return lastAddress;
    }

    return read(readBuffer, lastAddress, processor);
  }

  @Override
  public synchronized long lookUpApproximateAddress(
      final long position, final LongUnaryOperator positionReader) {
    try {
      return positions.floorKey(position);
    } catch (NullPointerException e) {
      return LogStorage.OP_RESULT_INVALID_ADDR;
    }
  }
}
