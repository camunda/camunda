/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.warmup;

import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * Offers one job stream for a single job type, as a gateway with a streaming worker would. Pushed
 * jobs are serialised as they would be for the wire, then handed to the workload.
 */
@NullMarked
final class ScratchJobStreamer implements JobStreamer {

  private final DirectBuffer jobType;
  private final JobStream stream;

  ScratchJobStreamer(
      final String jobType,
      final Map<String, Object> claims,
      final Consumer<ActivatedJob> pushedJobConsumer) {
    this.jobType = BufferUtil.wrapString(jobType);
    final var worker = BufferUtil.wrapString("leader-warmup");
    final var properties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(Duration.ofMinutes(5).toMillis())
            .setTenantIds(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
            .setClaims(claims);
    stream =
        new JobStream() {
          @Override
          public JobActivationProperties properties() {
            return properties;
          }

          @Override
          public void push(final ActivatedJob payload) {
            payload.write(new UnsafeBuffer(new byte[payload.getLength()]), 0);
            pushedJobConsumer.accept(payload);
          }
        };
  }

  @Override
  public Optional<JobStream> streamFor(
      final DirectBuffer jobType, final Predicate<JobActivationProperties> filter) {
    if (this.jobType.equals(jobType) && filter.test(stream.properties())) {
      return Optional.of(stream);
    }
    return Optional.empty();
  }
}
