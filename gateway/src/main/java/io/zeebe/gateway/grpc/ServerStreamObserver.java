/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.grpc;

import io.grpc.stub.StreamObserver;

/**
 * A simple extension of {@link StreamObserver}, meant to be used in conjunction with {@link
 * io.grpc.stub.ServerCallStreamObserver}. In order to avoid depending on {@link
 * io.grpc.stub.ServerCallStreamObserver}, which is experimental (as of now), we introduce this
 * simple interface, and we can easily change the implementation whenever the experimental API is
 * changed.
 *
 * @param <GrpcResponseT> the expected gRPC response type
 */
public interface ServerStreamObserver<GrpcResponseT> extends StreamObserver<GrpcResponseT> {
  boolean isCancelled();
}
