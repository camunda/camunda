/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api.dto;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.broker.client.api.BrokerClientException;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.IllegalBrokerResponseException;
import org.jspecify.annotations.Nullable;

public class BrokerResponse<T> {
  private static final String ILLEGAL_SUCCESS_RESPONSE_MESSAGE =
      "Expected broker response to be error or rejection, but received response";
  private static final String ILLEGAL_RESPONSE_MESSAGE =
      "Expected broker response to be either response, rejection, or error, but is neither of them";

  private final boolean isResponse;
  private final @Nullable T response;
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

  public @Nullable BrokerError getError() {
    return null;
  }

  public boolean isRejection() {
    return false;
  }

  public @Nullable BrokerRejection getRejection() {
    return null;
  }

  public boolean isResponse() {
    return isResponse;
  }

  public @Nullable T getResponse() {
    return response;
  }

  public T getResponseOrThrow() {
    if (isResponse()) {
      return requireNonNull(response, "response is null");
    }

    throw toException();
  }

  public BrokerClientException toException() {
    if (isResponse()) {
      return new IllegalBrokerResponseException(ILLEGAL_SUCCESS_RESPONSE_MESSAGE);
    } else if (isError()) {
      return new BrokerErrorException(requireNonNull(getError(), "error is null"));
    } else if (isRejection()) {
      return new BrokerRejectionException(requireNonNull(getRejection(), "rejection is null"));
    }

    return new IllegalBrokerResponseException(ILLEGAL_RESPONSE_MESSAGE);
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
