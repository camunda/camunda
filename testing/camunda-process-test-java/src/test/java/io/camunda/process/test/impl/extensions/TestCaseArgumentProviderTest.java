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

import io.camunda.process.test.api.testCases.TestCase;
import io.camunda.process.test.api.testCases.TestCaseArgumentProvider;
import io.camunda.process.test.api.testCases.TestCaseReadException;
import io.camunda.process.test.api.testCases.TestCaseSource;
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
public class TestCaseArgumentProviderTest {

  private static final String TEST_CASES_DIRECTORY = "/testCaseArgumentProviderTest";
  private static final String DEFAULT_FILE_EXTENSION = ".json";

  @Mock private ExtensionContext extensionContext;
  @Mock private ParameterDeclarations parameterDeclarations;

  @Mock private TestCaseSource testCaseSource;

  @BeforeEach
  void configureMocks() {
    //noinspection unchecked,rawtypes
    when(extensionContext.getRequiredTestClass()).thenReturn((Class) ProcessTest.class);
  }

  @Test
  void shouldProvideTestCaseArguments() {
    // given
    final TestCaseArgumentProvider argumentProvider = new TestCaseArgumentProvider();

    when(testCaseSource.directory()).thenReturn(TEST_CASES_DIRECTORY);
    when(testCaseSource.fileNames()).thenReturn(new String[] {});
    when(testCaseSource.fileExtension()).thenReturn(DEFAULT_FILE_EXTENSION);

    argumentProvider.accept(testCaseSource);

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
            tuple("Scenario 1: Test Case 1", "testCases1.json"),
            tuple("Scenario 1: Test Case 2", "testCases1.json"),
            tuple("Scenario 2: Test Case 1", "testCases2.json"));
  }

  @Test
  void shouldFilterByFileNames() {
    // given
    final TestCaseArgumentProvider argumentProvider = new TestCaseArgumentProvider();

    when(testCaseSource.directory()).thenReturn(TEST_CASES_DIRECTORY);
    when(testCaseSource.fileNames()).thenReturn(new String[] {"testCases1.json"});
    when(testCaseSource.fileExtension()).thenReturn(DEFAULT_FILE_EXTENSION);

    argumentProvider.accept(testCaseSource);

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
            tuple("Scenario 1: Test Case 1", "testCases1.json"),
            tuple("Scenario 1: Test Case 2", "testCases1.json"));
  }

  @Test
  void shouldFilterByFileExtension() {
    // given
    final TestCaseArgumentProvider argumentProvider = new TestCaseArgumentProvider();

    when(testCaseSource.directory()).thenReturn(TEST_CASES_DIRECTORY);
    when(testCaseSource.fileNames()).thenReturn(new String[] {});
    when(testCaseSource.fileExtension()).thenReturn(".test");

    argumentProvider.accept(testCaseSource);

    // when
    final Stream<? extends Arguments> argumentStream =
        argumentProvider.provideArguments(parameterDeclarations, extensionContext);

    // then
    assertThat(argumentStream)
        .hasSize(1)
        .extracting(Arguments::get)
        .allSatisfy(args -> assertThat(args).hasSize(2))
        .extracting(args -> ((TestCase) args[0]).getName(), args -> args[1])
        .contains(tuple("Scenario 3: Test Case 1", "testCases.test"));
  }

  @Test
  void shouldFilterByFileNamesAndIgnoreFileExtension() {
    // given
    final TestCaseArgumentProvider argumentProvider = new TestCaseArgumentProvider();

    when(testCaseSource.directory()).thenReturn(TEST_CASES_DIRECTORY);
    when(testCaseSource.fileNames()).thenReturn(new String[] {"testCases1.json"});
    when(testCaseSource.fileExtension()).thenReturn(".fun");

    argumentProvider.accept(testCaseSource);

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
            tuple("Scenario 1: Test Case 1", "testCases1.json"),
            tuple("Scenario 1: Test Case 2", "testCases1.json"));
  }

  @Test
  void shouldFailIfDirectoryDoesntExist() {
    // given
    final TestCaseArgumentProvider argumentProvider = new TestCaseArgumentProvider();

    final String directory = TEST_CASES_DIRECTORY + "/non-existing";

    when(testCaseSource.directory()).thenReturn(directory);
    when(testCaseSource.fileNames()).thenReturn(new String[] {});
    when(testCaseSource.fileExtension()).thenReturn(DEFAULT_FILE_EXTENSION);

    argumentProvider.accept(testCaseSource);

    // when/then
    assertThatThrownBy(
            () -> argumentProvider.provideArguments(parameterDeclarations, extensionContext))
        .isInstanceOf(TestCaseReadException.class)
        .hasMessageContaining("The directory '%s' does not exist.", directory);
  }

  @Test
  void shouldFailIfDirectoryIsEmpty() {
    // given
    final TestCaseArgumentProvider argumentProvider = new TestCaseArgumentProvider();

    final String fileExtension = ".fun";

    when(testCaseSource.directory()).thenReturn(TEST_CASES_DIRECTORY);
    when(testCaseSource.fileNames()).thenReturn(new String[] {});
    when(testCaseSource.fileExtension()).thenReturn(fileExtension);

    argumentProvider.accept(testCaseSource);

    // when/then
    assertThatThrownBy(
            () -> argumentProvider.provideArguments(parameterDeclarations, extensionContext))
        .isInstanceOf(TestCaseReadException.class)
        .hasMessageContaining(
            "No files found with extension '%s' in directory '%s'.",
            fileExtension, TEST_CASES_DIRECTORY);
  }

  @Test
  void shouldFailIfFilesDoesntExist() {
    // given
    final TestCaseArgumentProvider argumentProvider = new TestCaseArgumentProvider();

    when(testCaseSource.directory()).thenReturn(TEST_CASES_DIRECTORY);
    when(testCaseSource.fileNames())
        .thenReturn(new String[] {"testCases1.json", "non-existing.json"});
    when(testCaseSource.fileExtension()).thenReturn(DEFAULT_FILE_EXTENSION);

    argumentProvider.accept(testCaseSource);

    // when/then
    assertThatThrownBy(
            () -> argumentProvider.provideArguments(parameterDeclarations, extensionContext))
        .hasMessageContaining(
            "The directory '%s' doesn't contain the files: %s",
            TEST_CASES_DIRECTORY, "[non-existing.json]");
  }

  @Test
  void shouldFailIfFileIsInvalid() {
    // given
    final TestCaseArgumentProvider argumentProvider = new TestCaseArgumentProvider();

    when(testCaseSource.directory()).thenReturn(TEST_CASES_DIRECTORY);
    when(testCaseSource.fileNames()).thenReturn(new String[] {"testCases4.invalid"});
    when(testCaseSource.fileExtension()).thenReturn(DEFAULT_FILE_EXTENSION);

    argumentProvider.accept(testCaseSource);

    // when/then
    assertThatThrownBy(
            () ->
                argumentProvider
                    .provideArguments(parameterDeclarations, extensionContext)
                    .collect(Collectors.toList()))
        .hasMessageContaining("The file '%s' contains invalid test cases.", "testCases4.invalid");
  }

  private static final class ProcessTest {}
}
