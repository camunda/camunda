/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.job;

import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamActivatedJobsRequest;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestStreamObserver
    implements ClientResponseObserver<StreamActivatedJobsRequest, ActivatedJob> {

  private final List<ActivatedJob> streamedJobs = Collections.synchronizedList(new ArrayList<>());
  private final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
  private volatile boolean isClosed;
  private volatile ClientCallStreamObserver<StreamActivatedJobsRequest> requestStream;

  public List<Throwable> getErrors() {
    return errors;
  }

  public List<ActivatedJob> getStreamedJobs() {
    return streamedJobs;
  }

  @Override
  public void onNext(final ActivatedJob value) {
    streamedJobs.add(value);
  }

  @Override
  public void onError(final Throwable t) {
    errors.add(t);
    requestStream.onError(t);
    isClosed = true;
  }

  @Override
  public void onCompleted() {
    isClosed = true;
  }

  @Override
  public void beforeStart(
      final ClientCallStreamObserver<StreamActivatedJobsRequest> requestStream) {
    this.requestStream = requestStream;
  }

  public void cancel() {
    requestStream.cancel("test cancel", new RuntimeException());
    isClosed = true;
  }

  public boolean isClosed() {
    return isClosed;
  }
}
