/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.cmd;

/**
 * Represents an exceptional error that occurs when a partition can not be found. For example this
 * can happen when the element instance key does not refer to a known partition.
 */
public class PartitionNotFoundException extends ClientException {

  public static final String MESSAGE_TEMPLATE =
      "This command refers to an element that doesn't exist. The request targeted an element on "
          + "partition '%d', which cannot be found in the cluster. Check the command arguments.";

  public PartitionNotFoundException(final int partitionId) {
    super(String.format(MESSAGE_TEMPLATE, partitionId));
  }
}
