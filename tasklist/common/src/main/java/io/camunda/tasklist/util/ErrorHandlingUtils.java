/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import io.camunda.client.api.command.ProblemException;
import io.camunda.client.wrappers.ProblemDetail;
import java.util.Objects;

public abstract class ErrorHandlingUtils {

  public static final String INVALID_STATE = "INVALID_STATE";
  public static final String TASK_ALREADY_ASSIGNED = "TASK_ALREADY_ASSIGNED";
  public static final String TASK_IS_NOT_ACTIVE = "TASK_IS_NOT_ACTIVE";
  public static final String TASK_NOT_ASSIGNED = "TASK_NOT_ASSIGNED";
  public static final String TASK_NOT_ASSIGNED_TO_CURRENT_USER =
      "TASK_NOT_ASSIGNED_TO_CURRENT_USER";

  public static String getErrorMessage(final Throwable exception) {
    final String errorMessage;

    if (exception instanceof final ProblemException ex
        && Objects.equals(ex.details().getStatus(), 409)
        && INVALID_STATE.equals(ex.details().getTitle())) {
      final ProblemDetail problemDetail = ex.details();
      errorMessage = createErrorMessage(problemDetail.getTitle(), problemDetail.getDetail());
    } else if (isCausedByTimeoutException(exception)) {
      errorMessage =
          createErrorMessage(
              "TASK_PROCESSING_TIMEOUT", "The request timed out while processing the task.");
    } else {
      errorMessage = exception.getMessage();
    }
    return errorMessage;
  }

  public static String createErrorMessage(final String title, final String detail) {
    return String.format(
        """
      { "title": "%s",
        "detail": "%s"
      }
      """,
        title, detail);
  }

  public static boolean isCausedByTimeoutException(final Throwable throwable) {
    return throwable != null
        && throwable.getCause() != null
        && throwable.getCause().getCause() instanceof java.net.SocketTimeoutException;
  }
}
