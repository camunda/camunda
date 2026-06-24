/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

public class CamundaSearchClientException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public CamundaSearchClientException() {}

  public CamundaSearchClientException(final String message) {
    super(message);
  }

  public CamundaSearchClientException(final Throwable e) {
    super(e);
  }

  public CamundaSearchClientException(final String message, final Throwable e) {
    super(message, e);
  }
}
