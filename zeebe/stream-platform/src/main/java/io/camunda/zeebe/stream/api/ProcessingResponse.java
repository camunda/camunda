/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api;

import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;

/**
 * The response which has been created during processing, for a request which can be identified by
 * {@link #requestId} and {@link #requestStreamId}.
 */
public interface ProcessingResponse {

  /**
   * @return the id which together with the stream id identifies the request
   */
  long requestId();

  /**
   * @return the id of the stream on which the request was sent, together with the request id the
   *     request can be identified
   */
  int requestStreamId();

  /**
   * @return the value of the response which should be sent as answer of the request
   */
  RecordBatchEntry responseValue();
}
