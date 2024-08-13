/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.api.response;

public interface CorrelateMessageResponse {
  /**
   * Returns the record key of the message that was correlated.
   *
   * @return record key of the message.
   */
  Long getMessageKey();

  /**
   * Returns the tenant id of the message that was correlated.
   *
   * @return identifier of the tenant that owns the correlated message.
   */
  String getTenantId();

  /**
   * Returns the process instance key this messages was correlated with.
   *
   * @return key of the correlated process instance
   */
  Long getProcessInstanceKey();
}
