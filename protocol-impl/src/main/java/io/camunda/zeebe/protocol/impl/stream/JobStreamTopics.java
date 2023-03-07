/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.stream;

public enum JobStreamTopics {
  ADD("job-stream-add"),
  PUSH("job-stream-push"),
  REMOVE("job-stream-remove"),
  REMOVE_ALL("job-stream-remove-all"),
  JOB_AVAILABLE("jobsAvailable");

  private final String topic;

  JobStreamTopics(final String topic) {
    this.topic = topic;
  }

  public String topic() {
    return topic;
  }
}
