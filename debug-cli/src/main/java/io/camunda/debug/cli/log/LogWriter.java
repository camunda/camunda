/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.log;

import java.io.PrintStream;

// Minimal stub for LogWriter to allow LogPrintCommand to compile
public class LogWriter {
  public LogWriter(final PrintStream out, final LogContentReader reader) {}

  public void writeAsTable() {}
}
