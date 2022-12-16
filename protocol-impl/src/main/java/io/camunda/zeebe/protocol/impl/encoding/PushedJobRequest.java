/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.PushedJobDecoder;
import io.camunda.zeebe.protocol.record.PushedJobEncoder;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class PushedJobRequest implements BufferReader, BufferWriter {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final PushedJobEncoder bodyEncoder = new PushedJobEncoder();
  private final PushedJobDecoder bodyDecoder = new PushedJobDecoder();

  private final JobRecord job = new JobRecord();
  private final DirectBuffer jobBuffer = new UnsafeBuffer();
  private long key;

  public JobRecord job() {
    return job;
  }

  public PushedJobRequest job(final JobRecord job) {
    this.job.wrap(job);
    return this;
  }

  public long key() {
    return key;
  }

  public PushedJobRequest key(final long key) {
    this.key = key;
    return this;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    key = bodyDecoder.key();
    bodyDecoder.wrapJob(jobBuffer);
    job.wrap(jobBuffer);
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength()
        + bodyEncoder.sbeBlockLength()
        + PushedJobEncoder.jobHeaderLength()
        + job.getLength();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder.wrapAndApplyHeader(buffer, offset, headerEncoder).key(key);

    // to avoid re-allocating a temporary buffer, we'll simulate the encoder's writing approach
    // since SBE writes in order, your latest offset is always the `limit`
    // a job is serialized as a raw buffer prefixed with its length
    // so write the length at the current offset, write the buffer after it, then update the `limit`
    // so further writes will have the appropriate position
    final int jobLength = job.getLength();
    final int bodyOffset = bodyEncoder.limit();
    buffer.putInt(bodyOffset, jobLength);
    job.write(buffer, bodyOffset + PushedJobEncoder.jobHeaderLength());
    bodyEncoder.limit(bodyOffset + PushedJobEncoder.jobHeaderLength() + jobLength);
  }

  @Override
  public int hashCode() {
    return Objects.hash(job, jobBuffer, key);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PushedJobRequest that = (PushedJobRequest) o;
    return key == that.key && job.equals(that.job) && jobBuffer.equals(that.jobBuffer);
  }

  @Override
  public String toString() {
    return "PushedJobRequest{" + "job=" + job + ", key=" + key + '}';
  }
}
