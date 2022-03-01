/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

/**
 * This exception should be used to indicate that the transition was aborted intentionally and
 * should not be treated as a failure.
 */
public class RecoverablePartitionTransitionException extends RuntimeException {

  public RecoverablePartitionTransitionException(final String message) {
    super(message);
  }
}
