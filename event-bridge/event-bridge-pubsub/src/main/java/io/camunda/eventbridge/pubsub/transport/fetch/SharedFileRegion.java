/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.pubsub.transport.fetch;

import io.netty.channel.FileRegion;
import io.netty.util.AbstractReferenceCounted;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

public class SharedFileRegion extends AbstractReferenceCounted implements FileRegion {

  private final FileChannel fileChannel;
  private final long position;
  private final long count;
  private long transferred;

  public SharedFileRegion(FileChannel fileChannel, long position, long count) {
    this.fileChannel = fileChannel;
    this.position = position;
    this.count = count;
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
    // THE MAGIC FIX: DO ABSOLUTELY NOTHING.
    // We strictly leave the FileChannel open for other fetchers to use.
    // The channel is owned and managed by Zeebe's Segment lifecycle.
  }

  @Override
  public SharedFileRegion touch(Object hint) {
    return this;
  }
}
