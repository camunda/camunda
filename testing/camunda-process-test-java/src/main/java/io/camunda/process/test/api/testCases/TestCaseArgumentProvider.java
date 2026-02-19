/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.api.testCases;

import io.camunda.process.test.impl.extension.SourceTestCase;
import io.camunda.process.test.impl.testCases.TestCasesReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;
import org.junit.jupiter.params.support.ParameterDeclarations;

/**
 * A JUnit argument provider that reads test cases from files in a given directory or from specific
 * files. The test cases are parsed and each test case is provided as an argument of the type {@link
 * TestCase} to the parameterized test. The name of the source file is provided as a second
 * argument.
 *
 * <p>The provider reads the configuration from the {@link TestCaseSource} annotation on the
 * parameterized test method.
 */
public class TestCaseArgumentProvider
    implements ArgumentsProvider, AnnotationConsumer<TestCaseSource> {

  private static final FilenameFilter ACCEPT_ALL_FILES_FILTER = (dir, name) -> true;

  private final TestCasesReader testCasesReader = new TestCasesReader();

  private String sourceDirectory;
  private List<String> sourceFileNames;
  private String sourceFileExtension;

  @Override
  public void accept(final TestCaseSource testCaseSource) {
    Objects.requireNonNull(testCaseSource.directory(), "The source directory must not be null.");
    Objects.requireNonNull(testCaseSource.fileNames(), "The source file names must not be null.");
    Objects.requireNonNull(
        testCaseSource.fileExtension(), "The source file extension must not be null.");

    sourceDirectory = testCaseSource.directory();
    sourceFileNames = Arrays.asList(testCaseSource.fileNames());
    sourceFileExtension = testCaseSource.fileExtension();
  }

  @Override
  public Stream<? extends Arguments> provideArguments(
      final ParameterDeclarations parameters, final ExtensionContext context) {

    final File directory = readDirectory(context.getRequiredTestClass());
    final List<File> files = readFiles(directory);

    return files.stream()
        .flatMap(
            file ->
                parseTestCases(file).getTestCases().stream()
                    .map(testCase -> asArguments(testCase, file)));
  }

  private File readDirectory(final Class<?> testClass) {
    final URL resource = testClass.getResource(sourceDirectory);
    if (resource == null) {
      throw new TestCaseReadException(
          String.format("The directory '%s' does not exist.", sourceDirectory));
    }

    final File directory = new File(resource.getFile());

    // validate the directory
    if (!directory.isDirectory()) {
      throw new TestCaseReadException(
          String.format("The path '%s' is not a directory.", sourceDirectory));
    }
    if (!directory.canRead()) {
      throw new TestCaseReadException(
          String.format("The directory '%s' is not readable.", sourceDirectory));
    }
    return directory;
  }

  private List<File> readFiles(final File directory) {
    final FilenameFilter fileExtensionFilter = (dir, name) -> name.endsWith(sourceFileExtension);
    final FilenameFilter filenameFilter =
        sourceFileNames.isEmpty() ? fileExtensionFilter : ACCEPT_ALL_FILES_FILTER;

    final List<File> files =
        Optional.ofNullable(directory.listFiles(filenameFilter))
            .map(Arrays::asList)
            .filter(filesInDirectory -> !filesInDirectory.isEmpty())
            .orElseThrow(
                () ->
                    new TestCaseReadException(
                        String.format(
                            "No files found with extension '%s' in directory '%s'.",
                            sourceFileExtension, sourceDirectory)));

    if (sourceFileNames.isEmpty()) {
      return files;
    }

    // filter files by the specified names in the source annotation
    final List<File> existingSourceFiles =
        files.stream()
            .filter(file -> sourceFileNames.contains(file.getName()))
            .collect(Collectors.toList());

    if (existingSourceFiles.size() < sourceFileNames.size()) {
      final List<String> existingSourceFileNames =
          existingSourceFiles.stream().map(File::getName).collect(Collectors.toList());

      final List<String> missingSourceFileNames = new ArrayList<>(sourceFileNames);
      missingSourceFileNames.removeAll(existingSourceFileNames);

      if (!missingSourceFileNames.isEmpty()) {
        throw new TestCaseReadException(
            String.format(
                "The directory '%s' doesn't contain the files: %s",
                sourceDirectory, missingSourceFileNames));
      }
    }
    return existingSourceFiles;
  }

  private TestCases parseTestCases(final File testCasesFile) {
    try (final InputStream inputStream = Files.newInputStream(testCasesFile.toPath())) {
      return testCasesReader.read(inputStream);

    } catch (final Exception e) {
      throw new TestCaseReadException(
          String.format("The file '%s' contains invalid test cases.", testCasesFile.getName()), e);
    }
  }

  private static Arguments asArguments(final TestCase testCase, final File file) {
    return Arguments.of(new SourceTestCase(testCase), file.getName());
  }
}
