/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.api.spec;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.process.test.impl.spec.dsl.ProcessSpec;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

public class TestCaseArgumentsProvider
    implements ArgumentsProvider, AnnotationConsumer<CamundaProcessSpecSource> {

  private static final String SPEC_FILE_EXTENSION = ".spec";

  private final ObjectMapper objectMapper =
      new ObjectMapper().enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);

  private File specDirectory;
  private File specFile;

  private ProcessSpec parseSpec(final File testSpecification) {
    try {
      return objectMapper.readValue(testSpecification, ProcessSpec.class);
    } catch (final IOException e) {
      throw new RuntimeException(
          String.format("Failed to read specification: '%s'", testSpecification), e);
    }
  }

  @Override
  public void accept(final CamundaProcessSpecSource specSource) {

    if (!specSource.specFile().isEmpty()) {
      specFile = validateSpecFile(specSource.specFile());

    } else if (!specSource.specDirectory().isEmpty()) {
      specDirectory = validateSpecDirectory(specSource.specDirectory());

    } else {
      throw new IllegalArgumentException("Define either a SpecDirectory or a SpecFile");
    }
  }

  private File validateSpecDirectory(final String specDirectory) {
    final URL resource = getClass().getResource(specDirectory);
    if (resource == null) {
      throw new IllegalArgumentException(
          String.format("SpecDirectory '%s' not found or is empty", specDirectory));
    }

    try {
      final File file = new File(resource.toURI());
      if (!file.isDirectory()) {
        throw new IllegalArgumentException(
            String.format("SpecDirectory '%s' not a directory", specDirectory));
      }
      return file;

    } catch (final URISyntaxException e) {
      throw new IllegalArgumentException(
          String.format("Failed to access SpecDirectory '%s'", specDirectory), e);
    }
  }

  private File validateSpecFile(final String specFile) {
    final URL resource = getClass().getResource(specFile);
    if (resource == null) {
      throw new IllegalArgumentException(String.format("SpecFile '%s' not found", specFile));
    }

    try {
      final File file = new File(resource.toURI());
      if (!file.getName().endsWith(SPEC_FILE_EXTENSION)) {
        throw new IllegalArgumentException(
            String.format(
                "SpecFile '%s' doesn't have the file extension '%s'",
                specFile, SPEC_FILE_EXTENSION));
      }
      return file;

    } catch (final URISyntaxException e) {
      throw new IllegalArgumentException(
          String.format("Failed to access SpecFile '%s'", specFile), e);
    }
  }

  @Override
  public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {

    if (specDirectory != null) {
      final File[] specFiles =
          Objects.requireNonNull(
              specDirectory.listFiles((dir, name) -> name.endsWith(SPEC_FILE_EXTENSION)));

      return Arrays.stream(specFiles)
          .map(this::parseSpec)
          .flatMap(TestCaseArgumentsProvider::toArgumentsStream);

    } else if (specFile != null) {
      final ProcessSpec testSpecification = parseSpec(specFile);
      return toArgumentsStream(testSpecification);
    }

    return Stream.empty();
  }

  private static Stream<Arguments> toArgumentsStream(final ProcessSpec testSpecification) {
    final List<CamundaProcessSpecResource> testResources = testSpecification.getTestResources();
    return testSpecification.getTestCases().stream()
        .map(testCase -> Arguments.of(testCase, testResources));
  }
}
