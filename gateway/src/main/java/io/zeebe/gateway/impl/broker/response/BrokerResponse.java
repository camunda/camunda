/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.broker.response;

public class BrokerResponse<T> {

  private final boolean isResponse;
  private final T response;
  private final int partitionId;
  private final long key;

  protected BrokerResponse() {
    isResponse = false;
    response = null;
    partitionId = -1;
    key = -1;
  }

  public BrokerResponse(final T response) {
    this(response, -1, -1);
  }

  public BrokerResponse(final T response, final int partitionId, final long key) {
    isResponse = true;
    this.response = response;
    this.partitionId = partitionId;
    this.key = key;
  }

  public boolean isError() {
    return false;
  }

  public BrokerError getError() {
    return null;
  }

  public boolean isRejection() {
    return false;
  }

  public BrokerRejection getRejection() {
    return null;
  }

  public boolean isResponse() {
    return isResponse;
  }

  public T getResponse() {
    return response;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public long getKey() {
    return key;
  }

  @Override
  public String toString() {
    return "BrokerResponse{"
        + "isResponse="
        + isResponse
        + ", response="
        + response
        + ", partitionId="
        + partitionId
        + ", key="
        + key
        + '}';
  }
}
