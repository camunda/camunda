/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.broker;

import io.zeebe.gateway.cmd.ClientException;

public final class RequestRetriesExhaustedException extends ClientException {

  public RequestRetriesExhaustedException() {
    super(
        "Expected to execute the command on one of the partitions, but all failed; there are no more partitions available to retry. "
            + "Please try again. If the error persists contact your zeebe operator");
  }
}
