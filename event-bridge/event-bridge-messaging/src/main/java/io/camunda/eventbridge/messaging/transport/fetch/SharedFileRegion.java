/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.transport.fetch;

import io.netty.channel.FileRegion;
import io.netty.util.AbstractReferenceCounted;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

public class SharedFileRegion extends AbstractReferenceCounted implements FileRegion {

  private final FileChannel fileChannel;
  private final long position;
  private final long count;
  private final Runnable onRelease;
  private long transferred;

  /**
   * @param onRelease invoked exactly once when Netty releases this region — after the transfer
   *     completes or when a failed/closed channel discards it from the outbound buffer. This is the
   *     only completion signal the producer of the region gets; it must carry the segment lease
   *     release (see {@link ManagedFetchResponseAdapter}), never a channel close.
   */
  public SharedFileRegion(
      final FileChannel fileChannel,
      final long position,
      final long count,
      final Runnable onRelease) {
    this.fileChannel = fileChannel;
    this.position = position;
    this.count = count;
    this.onRelease = onRelease;
  }

  @Override
  public long position() {
    return position;
  }

  @Override
  public long transfered() {
    return transferred;
  }

  @Override
  public long transferred() {
    return transferred;
  }

  @Override
  public long count() {
    return count;
  }

  @Override
  public long transferTo(WritableByteChannel target, long position) throws IOException {
    long count = this.count - position;
    if (count < 0 || position < 0) {
      throw new IllegalArgumentException(
          "position out of range: " + position + " (expected: 0 - " + (this.count - 1) + ")");
    }
    if (count == 0) {
      return 0L;
    }

    // This is where the magic happens.
    // FileChannel.transferTo tells the OS to execute 'sendfile'.
    long written = fileChannel.transferTo(this.position + position, count, target);
    if (written > 0) {
      this.transferred += written;
    }
    return written;
  }

  @Override
  public SharedFileRegion retain() {
    super.retain();
    return this;
  }

  @Override
  public SharedFileRegion retain(int increment) {
    super.retain(increment);
    return this;
  }

  @Override
  public SharedFileRegion touch() {
    return this;
  }

  @Override
  protected void deallocate() {
    // Deliberately leave the FileChannel open — it is owned by the segment lifecycle and shared
    // with other fetchers. But DO forward the release signal: Netty calls this exactly once per
    // region (after the transfer, or when discarding it on a failed/closing channel), and it is
    // the only notification the fetch path gets that the segment lease may be released. Swallowing
    // it pinned every served segment forever (compacted segments were renamed *-deleted but never
    // unlinked, because the deferred deletion waits for the lease count to reach zero).
    if (onRelease != null) {
      onRelease.run();
    }
  }

  @Override
  public SharedFileRegion touch(Object hint) {
    return this;
  }
}
