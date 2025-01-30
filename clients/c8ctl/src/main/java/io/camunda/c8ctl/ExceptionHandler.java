/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c8ctl;

import io.camunda.c8ctl.mixin.OutputMixin;
import io.camunda.c8ctl.serde.JsonOutputFormatter;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.ProblemDetail;
import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;

public final class ExceptionHandler implements IExecutionExceptionHandler {

  @Override
  public int handleExecutionException(
      final Exception ex, final CommandLine commandLine, final ParseResult fullParseResult)
      throws Exception {
    if (ex instanceof final ProblemException problemException) {
      final JsonOutputFormatter formatter = new JsonOutputFormatter(OutputMixin.writer());
      formatter.write(problemException.details(), ProblemDetail.class);
      return 1;
    }

    throw ex;
  }
}
