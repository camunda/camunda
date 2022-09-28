/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.agrona.LangUtil;
import org.apache.maven.plugin.surefire.booterclient.output.InPluginProcessDumpSingleton;
import org.apache.maven.plugin.surefire.report.ConsoleOutputFileReporter;
import org.apache.maven.plugin.surefire.report.FileReporter;
import org.apache.maven.surefire.api.report.TestOutputReportEntry;
import org.apache.maven.surefire.api.report.TestSetReportEntry;
import org.apache.maven.surefire.extensions.ConsoleOutputReportEventListener;

/**
 * A {@link ConsoleOutputReportEventListener} which will save a test's previous run's output instead
 * of overwriting it. This is somewhat hacky, as the canonical implementation {@link
 * ConsoleOutputFileReporter} is not really extensible. So instead we "predict" the output path that
 * it will use, and if it was present, we copy it in order to preserve it. If we see that the
 * canonical implementation changes too much, then we could simply make our own to stabilize things,
 * but for now I think this is acceptable.
 *
 * <p>By using this implementation, it now works like this:
 *
 * <ul>
 *   <li>All calls are delegated to an instance of the canonical implementation
 *   <li>When {@link #writeTestOutput(TestOutputReportEntry)} is called, we predict the path by
 *       calling {@link FileReporter#getReportFile(File, String, String, String)} via reflection
 *       just like the canonical implementation does
 *   <li>If a file exists at this path, extract the latest run count from the other existing files;
 *       we opted against keeping an in-memory cache, as on long builds this could use too much
 *       memory
 */
public final class ZeebeConsoleOutputFileReporter implements ConsoleOutputReportEventListener {

  private static final Pattern RUN_COUNT_MATCHER = Pattern.compile("^.+?-(\\d)-output\\.txt$");
  private static final String OUTPUT_FILE_EXTENSION = "-output.txt";

  private final File reportsDirectory;
  private final String reportNameSuffix;
  private final boolean usePhrasedFileName;
  private final Integer forkNumber;
  private final ConsoleOutputReportEventListener delegate;

  private volatile String reportEntryName;

  public ZeebeConsoleOutputFileReporter(
      final File reportsDirectory,
      final String reportNameSuffix,
      final boolean usePhrasedFileName,
      final Integer forkNumber,
      final ConsoleOutputReportEventListener delegate) {
    this.reportsDirectory = reportsDirectory;
    this.reportNameSuffix = reportNameSuffix;
    this.usePhrasedFileName = usePhrasedFileName;
    this.forkNumber = forkNumber;
    this.delegate = delegate;
  }

  @Override
  public synchronized void testSetStarting(final TestSetReportEntry reportEntry) {
    reportEntryName =
        usePhrasedFileName ? reportEntry.getSourceText() : reportEntry.getSourceName();
    delegate.testSetStarting(reportEntry);
  }

  @Override
  public synchronized void testSetCompleted(final TestSetReportEntry report) {
    delegate.testSetStarting(report);
  }

  @Override
  public synchronized void close() {
    delegate.close();
  }

  @Override
  public synchronized void writeTestOutput(final TestOutputReportEntry reportEntry) {
    try {
      final Path reportPath = getReportPath();
      final String fileName =
          reportPath.getFileName().toString().replace(OUTPUT_FILE_EXTENSION, "");
      final Path backupPath =
          reportPath.resolveSibling(
              fileName + "-" + computeRunCount(reportPath, fileName) + OUTPUT_FILE_EXTENSION);

      // write the report first; if anything was written, we then move it with the appropriate run
      // count in its name
      delegate.writeTestOutput(reportEntry);
      if (Files.exists(reportPath)) {
        Files.move(reportPath, backupPath);
      }

    } catch (final Exception e) {
      dumpException(e);
      LangUtil.rethrowUnchecked(e);
    }
  }

  private void dumpException(final Exception e) {
    if (forkNumber == null) {
      InPluginProcessDumpSingleton.getSingleton()
          .dumpException(e, e.getLocalizedMessage(), reportsDirectory);
    } else {
      InPluginProcessDumpSingleton.getSingleton()
          .dumpException(e, e.getLocalizedMessage(), reportsDirectory, forkNumber);
    }
  }

  private int computeRunCount(final Path path, final String fileName) throws IOException {
    final Path directory = path.getParent();
    int latestRunCount = 0;

    try (final DirectoryStream<Path> files =
        Files.newDirectoryStream(directory, p -> filterTestOutputFiles(fileName, p))) {
      for (final Path file : files) {
        final Matcher matcher = RUN_COUNT_MATCHER.matcher(file.getFileName().toString());
        if (matcher.find()) {
          final String runCount = matcher.group(1);
          latestRunCount = Math.max(latestRunCount, Integer.parseInt(runCount));
        }
      }
    }

    return latestRunCount + 1;
  }

  private Path getReportPath()
      throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    final Method delegate =
        FileReporter.class.getDeclaredMethod(
            "getReportFile", File.class, String.class, String.class, String.class);
    delegate.setAccessible(true);
    final File file =
        (File)
            delegate.invoke(
                null, reportsDirectory, reportEntryName, reportNameSuffix, OUTPUT_FILE_EXTENSION);

    return file.toPath();
  }

  private boolean filterTestOutputFiles(final String fileName, final Path path) {
    return path.getFileName().toString().startsWith(fileName)
        && path.getFileName().toString().endsWith(OUTPUT_FILE_EXTENSION);
  }
}
