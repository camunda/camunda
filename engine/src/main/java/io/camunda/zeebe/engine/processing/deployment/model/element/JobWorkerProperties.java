/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import org.agrona.DirectBuffer;

/**
 * The properties of an element that is based on a job and should be processed by a job worker. For
 * example, a service task.
 */
public class JobWorkerProperties {

  private Expression type;
  private Expression retries;
  private DirectBuffer encodedHeaders = JobRecord.NO_HEADERS;

  public Expression getType() {
    return type;
  }

  public void setType(final Expression type) {
    this.type = type;
  }

  public Expression getRetries() {
    return retries;
  }

  public void setRetries(final Expression retries) {
    this.retries = retries;
  }

  public DirectBuffer getEncodedHeaders() {
    return encodedHeaders;
  }

  public void setEncodedHeaders(final DirectBuffer encodedHeaders) {
    this.encodedHeaders = encodedHeaders;
  }
}
