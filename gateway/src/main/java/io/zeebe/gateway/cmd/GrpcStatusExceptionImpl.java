/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway.cmd;

import io.grpc.Status;

/**
 * Simple implementation of {@link GrpcStatusException} when wrapping specific errors for which we
 * want to return a specific {@link Status}.
 */
public class GrpcStatusExceptionImpl extends ClientException implements GrpcStatusException {
  private static final long serialVersionUID = -7333429675023512630L;

  private final Status status;

  public GrpcStatusExceptionImpl(String message, Status status) {
    this(message, status, null);
  }

  public GrpcStatusExceptionImpl(String message, Status status, Throwable cause) {
    super(message, cause);
    this.status = status.augmentDescription(message);
  }

  @Override
  public Status getGrpcStatus() {
    return status;
  }
}
