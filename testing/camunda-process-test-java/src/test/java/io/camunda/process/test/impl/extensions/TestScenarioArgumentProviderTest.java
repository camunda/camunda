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
package io.camunda.process.test.impl.extensions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.when;

import io.camunda.process.test.api.dsl.TestCase;
import io.camunda.process.test.api.dsl.TestScenarioArgumentProvider;
import io.camunda.process.test.api.dsl.TestScenarioReadException;
import io.camunda.process.test.api.dsl.TestScenarioSource;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.support.ParameterDeclarations;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TestScenarioArgumentProviderTest {

  private static final String SCENARIO_DIRECTORY = "/testScenarioArgumentProviderTest";
  private static final String DEFAULT_FILE_EXTENSION = ".json";

  @Mock private ExtensionContext extensionContext;
  @Mock private ParameterDeclarations parameterDeclarations;

  @Mock private TestScenarioSource testScenarioSource;

  @BeforeEach
  void configureMocks() {
    //noinspection unchecked,rawtypes
    when(extensionContext.getRequiredTestClass()).thenReturn((Class) ProcessTest.class);
  }

  @Test
  void shouldProvideScenarioArguments() {
    // given
    final TestScenarioArgumentProvider argumentProvider = new TestScenarioArgumentProvider();

    when(testScenarioSource.directory()).thenReturn(SCENARIO_DIRECTORY);
    when(testScenarioSource.fileNames()).thenReturn(new String[] {});
    when(testScenarioSource.fileExtension()).thenReturn(DEFAULT_FILE_EXTENSION);

    argumentProvider.accept(testScenarioSource);

    // when
    final Stream<? extends Arguments> argumentStream =
        argumentProvider.provideArguments(parameterDeclarations, extensionContext);

    // then
    assertThat(argumentStream)
        .hasSize(3)
        .extracting(Arguments::get)
        .allSatisfy(args -> assertThat(args).hasSize(2))
        .extracting(args -> ((TestCase) args[0]).getName(), args -> args[1])
        .contains(
            tuple("Scenario 1: Test Case 1", "scenario1.json"),
            tuple("Scenario 1: Test Case 2", "scenario1.json"),
            tuple("Scenario 2: Test Case 1", "scenario2.json"));
  }

  @Test
  void shouldFilterByFileNames() {
    // given
    final TestScenarioArgumentProvider argumentProvider = new TestScenarioArgumentProvider();

    when(testScenarioSource.directory()).thenReturn(SCENARIO_DIRECTORY);
    when(testScenarioSource.fileNames()).thenReturn(new String[] {"scenario1.json"});
    when(testScenarioSource.fileExtension()).thenReturn(DEFAULT_FILE_EXTENSION);

    argumentProvider.accept(testScenarioSource);

    // when
    final Stream<? extends Arguments> argumentStream =
        argumentProvider.provideArguments(parameterDeclarations, extensionContext);

    // then
    assertThat(argumentStream)
        .hasSize(2)
        .extracting(Arguments::get)
        .allSatisfy(args -> assertThat(args).hasSize(2))
        .extracting(args -> ((TestCase) args[0]).getName(), args -> args[1])
        .contains(
            tuple("Scenario 1: Test Case 1", "scenario1.json"),
            tuple("Scenario 1: Test Case 2", "scenario1.json"));
  }

  @Test
  void shouldFilterByFileExtension() {
    // given
    final TestScenarioArgumentProvider argumentProvider = new TestScenarioArgumentProvider();

    when(testScenarioSource.directory()).thenReturn(SCENARIO_DIRECTORY);
    when(testScenarioSource.fileNames()).thenReturn(new String[] {});
    when(testScenarioSource.fileExtension()).thenReturn(".scenario");

    argumentProvider.accept(testScenarioSource);

    // when
    final Stream<? extends Arguments> argumentStream =
        argumentProvider.provideArguments(parameterDeclarations, extensionContext);

    // then
    assertThat(argumentStream)
        .hasSize(1)
        .extracting(Arguments::get)
        .allSatisfy(args -> assertThat(args).hasSize(2))
        .extracting(args -> ((TestCase) args[0]).getName(), args -> args[1])
        .contains(tuple("Scenario 3: Test Case 1", "scenario3.scenario"));
  }

  @Test
  void shouldFilterByFileNamesAndIgnoreFileExtension() {
    // given
    final TestScenarioArgumentProvider argumentProvider = new TestScenarioArgumentProvider();

    when(testScenarioSource.directory()).thenReturn(SCENARIO_DIRECTORY);
    when(testScenarioSource.fileNames()).thenReturn(new String[] {"scenario1.json"});
    when(testScenarioSource.fileExtension()).thenReturn(".fun");

    argumentProvider.accept(testScenarioSource);

    // when
    final Stream<? extends Arguments> argumentStream =
        argumentProvider.provideArguments(parameterDeclarations, extensionContext);

    // then
    assertThat(argumentStream)
        .hasSize(2)
        .extracting(Arguments::get)
        .allSatisfy(args -> assertThat(args).hasSize(2))
        .extracting(args -> ((TestCase) args[0]).getName(), args -> args[1])
        .contains(
            tuple("Scenario 1: Test Case 1", "scenario1.json"),
            tuple("Scenario 1: Test Case 2", "scenario1.json"));
  }

  @Test
  void shouldFailIfDirectoryDoesntExist() {
    // given
    final TestScenarioArgumentProvider argumentProvider = new TestScenarioArgumentProvider();

    final String directory = SCENARIO_DIRECTORY + "/non-existing";

    when(testScenarioSource.directory()).thenReturn(directory);
    when(testScenarioSource.fileNames()).thenReturn(new String[] {});
    when(testScenarioSource.fileExtension()).thenReturn(DEFAULT_FILE_EXTENSION);

    argumentProvider.accept(testScenarioSource);

    // when/then
    assertThatThrownBy(
            () -> argumentProvider.provideArguments(parameterDeclarations, extensionContext))
        .isInstanceOf(TestScenarioReadException.class)
        .hasMessageContaining("The directory '%s' does not exist.", directory);
  }

  @Test
  void shouldFailIfDirectoryIsEmpty() {
    // given
    final TestScenarioArgumentProvider argumentProvider = new TestScenarioArgumentProvider();

    final String fileExtension = ".fun";

    when(testScenarioSource.directory()).thenReturn(SCENARIO_DIRECTORY);
    when(testScenarioSource.fileNames()).thenReturn(new String[] {});
    when(testScenarioSource.fileExtension()).thenReturn(fileExtension);

    argumentProvider.accept(testScenarioSource);

    // when/then
    assertThatThrownBy(
            () -> argumentProvider.provideArguments(parameterDeclarations, extensionContext))
        .isInstanceOf(TestScenarioReadException.class)
        .hasMessageContaining(
            "No files found with extension '%s' in directory '%s'.",
            fileExtension, SCENARIO_DIRECTORY);
  }

  @Test
  void shouldFailIfFilesDoesntExist() {
    // given
    final TestScenarioArgumentProvider argumentProvider = new TestScenarioArgumentProvider();

    when(testScenarioSource.directory()).thenReturn(SCENARIO_DIRECTORY);
    when(testScenarioSource.fileNames())
        .thenReturn(new String[] {"scenario1.json", "non-existing.json"});
    when(testScenarioSource.fileExtension()).thenReturn(DEFAULT_FILE_EXTENSION);

    argumentProvider.accept(testScenarioSource);

    // when/then
    assertThatThrownBy(
            () -> argumentProvider.provideArguments(parameterDeclarations, extensionContext))
        .hasMessageContaining(
            "The directory '%s' doesn't contain the files: %s",
            SCENARIO_DIRECTORY, "[non-existing.json]");
  }

  @Test
  void shouldFailIfFileIsInvalid() {
    // given
    final TestScenarioArgumentProvider argumentProvider = new TestScenarioArgumentProvider();

    when(testScenarioSource.directory()).thenReturn(SCENARIO_DIRECTORY);
    when(testScenarioSource.fileNames()).thenReturn(new String[] {"scenario4.invalid"});
    when(testScenarioSource.fileExtension()).thenReturn(DEFAULT_FILE_EXTENSION);

    argumentProvider.accept(testScenarioSource);

    // when/then
    assertThatThrownBy(
            () ->
                argumentProvider
                    .provideArguments(parameterDeclarations, extensionContext)
                    .collect(Collectors.toList()))
        .hasMessageContaining("The file '%s' is not a valid test scenario.", "scenario4.invalid");
  }

  private static final class ProcessTest {}
}
