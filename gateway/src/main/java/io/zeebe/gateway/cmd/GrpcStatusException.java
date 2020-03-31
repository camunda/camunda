/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.cmd;

import io.grpc.Status;

/**
 * Exceptions liable to be thrown during gRPC request/response cycle should implement this in order
 * to control which status is returned to the user.
 */
public interface GrpcStatusException {
  /** @return the status to return if thrown during request/response processing */
  Status getGrpcStatus();
}
