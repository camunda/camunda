/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.camunda.zeebe.util.Either;

/** Serializes rebalance requests to be forwarded to the coordinator. */
public interface RebalanceRequestsSerializer {

  byte[] encodeTriggerRebalanceRequest(TriggerRebalanceRequest request);

  TriggerRebalanceRequest decodeTriggerRebalanceRequest(byte[] encodedRequest);

  byte[] encodeResponse(RebalanceStatus response);

  byte[] encodeResponse(CancelRebalanceResponse response);

  byte[] encodeResponse(RebalanceErrorResponse response);

  Either<RebalanceErrorResponse, RebalanceStatus> decodeRebalanceStatusResponse(
      byte[] encodedResponse);

  Either<RebalanceErrorResponse, CancelRebalanceResponse> decodeCancelRebalanceResponse(
      byte[] encodedResponse);
}
