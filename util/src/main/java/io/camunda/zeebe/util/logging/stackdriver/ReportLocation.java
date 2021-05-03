/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.logging.stackdriver;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Additional information about the source code location that produced the log entry.
 *
 * <p>https://cloud.google.com/error-reporting/docs/formatting-error-messages
 */
@JsonInclude(Include.NON_EMPTY)
public final class ReportLocation {
  @JsonProperty("functionName")
  private String functionName;

  @JsonProperty("filePath")
  private String filePath;

  @JsonProperty("lineNumber")
  private int lineNumber;

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(final String filePath) {
    this.filePath = filePath;
  }

  public String getFunctionName() {
    return functionName;
  }

  public void setFunctionName(final String functionName) {
    this.functionName = functionName;
  }

  public int getLineNumber() {
    return lineNumber;
  }

  public void setLineNumber(final int lineNumber) {
    this.lineNumber = lineNumber;
  }
}
