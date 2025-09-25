/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.jobstream;

import io.camunda.zeebe.engine.common.processing.streamprocessor.JobStreamer.JobStream;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.transport.stream.api.RemoteStream;

final class RemoteJobStream implements JobStream {

  private final RemoteStream<JobActivationProperties, ActivatedJob> remoteStream;

  RemoteJobStream(final RemoteStream<JobActivationProperties, ActivatedJob> remoteStream) {
    this.remoteStream = remoteStream;
  }

  @Override
  public JobActivationProperties properties() {
    return remoteStream.metadata();
  }

  @Override
  public void push(final ActivatedJob job) {
    remoteStream.push(job);
  }
}
