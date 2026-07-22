/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import io.camunda.analytics.lake.sink.BatchEncoder;
import io.camunda.analytics.lake.sink.DataFileResult;
import io.camunda.analytics.lake.sink.SortedRun;
import io.camunda.analytics.lake.sink.TableSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.iceberg.Metrics;

/**
 * Records every file opened and every append/finish/abort call, in call order, into a shared log
 * (when one is supplied) so tests can assert cross-object ordering (e.g. riders before appends).
 */
final class FakeBatchEncoderFactory implements BatchEncoder.Factory {

  private final List<String> log;
  private final AtomicInteger nextId = new AtomicInteger();

  final List<FakeBatchEncoder> created = new ArrayList<>();

  /** Copied into every encoder's own flag at creation time; set before rows are fed. */
  final AtomicBoolean throwOnAppendForNextEncoders = new AtomicBoolean(false);

  FakeBatchEncoderFactory() {
    this(new ArrayList<>());
  }

  FakeBatchEncoderFactory(final List<String> log) {
    this.log = log;
  }

  @Override
  public BatchEncoder newFile(final TableSchema schema, final long epochDay) {
    final FakeBatchEncoder encoder =
        new FakeBatchEncoder(schema, epochDay, nextId.incrementAndGet(), log);
    encoder.throwOnAppend.set(throwOnAppendForNextEncoders.get());
    created.add(encoder);
    log.add("open(day=" + epochDay + ")");
    return encoder;
  }

  /** Every value ever appended to any encoder this factory created, in creation+append order. */
  List<Long> allAppendedValuesInOrder() {
    final List<Long> all = new ArrayList<>();
    for (final FakeBatchEncoder encoder : created) {
      all.addAll(encoder.appendedValues);
    }
    return all;
  }

  static final class FakeBatchEncoder implements BatchEncoder {

    final TableSchema schema;
    final long epochDay;
    final int id;
    final List<Long> appendedValues = new ArrayList<>();
    final AtomicBoolean throwOnAppend = new AtomicBoolean(false);
    final AtomicBoolean throwOnFinish = new AtomicBoolean(false);

    private final List<String> log;

    volatile boolean finished;
    volatile boolean aborted;

    private FakeBatchEncoder(
        final TableSchema schema, final long epochDay, final int id, final List<String> log) {
      this.schema = schema;
      this.epochDay = epochDay;
      this.id = id;
      this.log = log;
    }

    @Override
    public void append(final SortedRun run, final int fromIndex, final int toIndex) {
      if (throwOnAppend.get()) {
        throw new IllegalStateException("synthetic encoder append failure, file " + id);
      }
      for (int i = fromIndex; i < toIndex; i++) {
        appendedValues.add(run.longAt(TestPipelines.VALUE_COLUMN, i));
      }
      log.add("append(file=" + id + ",rows=" + (toIndex - fromIndex) + ")");
    }

    @Override
    public DataFileResult finish() {
      if (throwOnFinish.get()) {
        throw new IllegalStateException("synthetic encoder finish failure, file " + id);
      }
      finished = true;
      log.add("finish(file=" + id + ")");
      return new DataFileResult(
          schema.table(),
          "fake:/" + schema.table() + "/" + id,
          appendedValues.size(),
          appendedValues.size() * 200L,
          new Metrics((long) appendedValues.size()),
          epochDay);
    }

    @Override
    public void abort() {
      aborted = true;
      log.add("abort(file=" + id + ")");
    }
  }
}
