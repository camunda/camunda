/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn;

/**
 * Something went wrong during the process processing. This kind of exception should not be handled.
 */
public final class BpmnProcessingException extends RuntimeException {

  private static final String CONTEXT_POSTFIX = " [context: %s]";

  /**
   * The failure message of the exception is build from the given context and the message.
   *
   * @param context process instance-related data of the element that is executed
   * @param message the failure message
   */
  public BpmnProcessingException(final BpmnElementContext context, final String message) {
    super(message + String.format(CONTEXT_POSTFIX, context));
  }
}
