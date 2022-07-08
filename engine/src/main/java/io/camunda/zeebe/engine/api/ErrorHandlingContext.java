/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api;

// TODO check after refactoring, whether this is the same as ProcessingContext
// TODO Wait whether this interface will get more stuff (e.g. an object to schedule tasks), or
// whether it is just the result builder, in which case the interface is obsolete
public interface ErrorHandlingContext {
  ProcessingResultBuilder getProcessingResultBuilder();
}
