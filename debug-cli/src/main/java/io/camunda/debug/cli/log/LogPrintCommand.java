/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.log;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "print", description = "Print's the complete log to standard out")
public class LogPrintCommand implements Callable<Integer>, AutoCloseable {

  final LogFactory logFactory = new LogFactory();
  @Spec private CommandLine.Model.CommandSpec spec;

  @Option(
      names = {"-f", "--format"},
      description =
          "Print's the complete log in the specified format, defaults to json. Possible values: [ ${COMPLETION-CANDIDATES} ]",
      defaultValue = "JSON")
  private Format format;

  @Option(
      names = {"--from", "--fromPosition"},
      description =
          "Option to skip the begin of log and only print from the given position. Note this is on best effort basis, since engine records are written in batches. There might be some more records printed before the given position (part of the written batch).",
      defaultValue = "0")
  private long fromPosition;

  @Option(
      names = {"--to", "--toPosition"},
      description =
          "Option to set a limit to print the log only to the given position. Note this is on best effort basis, since engine records are written in batches. There might be some more records printed after the given position (part of the written batch).",
      defaultValue = "9223372036854775807") // Long.MAX_VALUE
  private long toPosition;

  @Option(
      names = {"--instanceKey"},
      description =
          "Filter to print only records which are part the specified process instance. Note this is on best effort basis, since engine records are written in batches. There might be some records printed which do not have an process instance key assigned. RaftRecords are completely skipped, if this filter is applied.",
      defaultValue = "0")
  private long instanceKey;

  @Override
  public Integer call() {
    final Path partitionPath = spec.findOption("-p").getValue();
    final LogContentReader logContentReader = new LogContentReader(logFactory, partitionPath);
    switch (format) {
      case DOT:
        final var logContent = logContentReader.readAll();
        System.out.println(logContent.asDotFile());
        break;
      case TABLE:
        printTable(logContentReader);
        break;
      default:
        printJson(logContentReader);
    }
    return 0;
  }

  private void printTable(final LogContentReader logContentReader) {
    logContentReader.seekToPosition(fromPosition);
    logContentReader.limitToPosition(toPosition);
    if (instanceKey > 0) {
      logContentReader.filterForProcessInstance(instanceKey);
    }
    new LogWriter(System.out, logContentReader).writeAsTable();
  }

  private void printJson(final LogContentReader logContentReader) {
    System.out.println("[");
    logContentReader.seekToPosition(fromPosition);
    logContentReader.limitToPosition(toPosition);
    if (instanceKey > 0) {
      logContentReader.filterForProcessInstance(instanceKey);
    }
    var separator = "";
    while (logContentReader.hasNext()) {
      final var record = logContentReader.next();
      System.out.print(separator + record);
      separator = ",\n";
    }
    System.out.println("\n]");
  }

  @Override
  public void close() throws Exception {
    logFactory.close();
  }

  public enum Format {
    JSON,
    DOT,
    TABLE
  }
}
