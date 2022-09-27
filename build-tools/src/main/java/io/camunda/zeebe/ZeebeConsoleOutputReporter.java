/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe;

import java.io.File;
import org.apache.maven.plugin.surefire.extensions.junit5.JUnit5ConsoleOutputReporter;
import org.apache.maven.surefire.extensions.ConsoleOutputReportEventListener;

public final class ZeebeConsoleOutputReporter extends JUnit5ConsoleOutputReporter {

  @Override
  public ConsoleOutputReportEventListener createListener(
      final File reportsDirectory, final String reportNameSuffix, final Integer forkNumber) {
    return new ZeebeConsoleOutputFileReporter(
        reportsDirectory,
        reportNameSuffix,
        false,
        forkNumber,
        super.createListener(reportsDirectory, reportNameSuffix, forkNumber));
  }

  @Override
  public String toString() {
    return "ZeebeConsoleOutputReporter{"
        + "disable="
        + isDisable()
        + ", encoding="
        + getEncoding()
        + ", usePhrasedFileName="
        + isUsePhrasedFileName()
        + '}';
  }
}
