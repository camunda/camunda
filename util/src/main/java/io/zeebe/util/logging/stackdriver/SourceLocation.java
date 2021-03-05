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
 * <p>https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry#logentrysourcelocation
 */
@JsonInclude(Include.NON_EMPTY)
final class SourceLocation {
  @JsonProperty("function")
  private String function;

  @JsonProperty("file")
  private String file;

  @JsonProperty("line")
  private int line;

  public String getFile() {
    return file;
  }

  public void setFile(final String file) {
    this.file = file;
  }

  public String getFunction() {
    return function;
  }

  public void setFunction(final String function) {
    this.function = function;
  }

  public int getLine() {
    return line;
  }

  public void setLine(final int line) {
    this.line = line;
  }
}
