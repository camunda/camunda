/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class ExecutableServiceTask extends ExecutableActivity {

  private DirectBuffer type;
  private int retries;
  private DirectBuffer encodedHeaders = JobRecord.NO_HEADERS;

  public ExecutableServiceTask(String id) {
    super(id);
  }

  public DirectBuffer getType() {
    return type;
  }

  public void setType(String type) {
    this.type = BufferUtil.wrapString(type);
  }

  public int getRetries() {
    return retries;
  }

  public void setRetries(int retries) {
    this.retries = retries;
  }

  public DirectBuffer getEncodedHeaders() {
    return encodedHeaders;
  }

  public void setEncodedHeaders(DirectBuffer encodedHeaders) {
    this.encodedHeaders = encodedHeaders;
  }
}
