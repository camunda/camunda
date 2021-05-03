/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.api.job;

import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.test.util.MsgPackUtil;
import org.agrona.DirectBuffer;

public class JobRequestStub {

  public static final long KEY = 789;
  public static final long DEADLINE = 123;
  public static final String TYPE = "type";
  public static final String WORKER = "worker";
  public static final DirectBuffer VARIABLES = MsgPackUtil.asMsgPack("key", "val");
  public static final DirectBuffer CUSTOM_HEADERS = MsgPackUtil.asMsgPack("headerKey", "headerVal");
  public static final int RETRIES = 456;

  public long getKey() {
    return KEY;
  }

  public long getDeadline() {
    return DEADLINE;
  }

  public String getType() {
    return TYPE;
  }

  public String getWorker() {
    return WORKER;
  }

  public DirectBuffer getVariables() {
    return VARIABLES;
  }

  public DirectBuffer getCustomHeaders() {
    return CUSTOM_HEADERS;
  }

  protected JobRecord buildDefaultValue() {
    final JobRecord value = new JobRecord();
    value.setCustomHeaders(CUSTOM_HEADERS);
    value.setDeadline(DEADLINE);
    value.setVariables(VARIABLES);
    value.setRetries(RETRIES);
    value.setType(TYPE);
    value.setWorker(WORKER);

    return value;
  }
}
