/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

public final class RequestRetriesExhaustedException extends BrokerClientException {

  public RequestRetriesExhaustedException() {
    super(
        "Expected to execute the command on one of the partitions, but all failed; there are no more partitions available to retry. "
            + "Please try again. If the error persists contact your zeebe operator");
  }
}
