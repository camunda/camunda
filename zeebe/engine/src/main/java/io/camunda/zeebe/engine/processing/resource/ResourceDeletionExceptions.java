/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

final class ResourceDeletionExceptions {

  private ResourceDeletionExceptions() {}

  static final class NoSuchResourceException extends IllegalStateException {
    private static final String ERROR_MESSAGE =
        "Expected to delete resource but no resource found with key `%d`";

    NoSuchResourceException(final long resourceKey) {
      super(String.format(ERROR_MESSAGE, resourceKey));
    }
  }

  static final class ActiveProcessInstancesException extends IllegalStateException {
    private static final String ERROR_MESSAGE =
        "Expected to delete resource with key `%d` but there are still running instances";

    ActiveProcessInstancesException(final long processDefinitionKey) {
      super(String.format(ERROR_MESSAGE, processDefinitionKey));
    }
  }
}
