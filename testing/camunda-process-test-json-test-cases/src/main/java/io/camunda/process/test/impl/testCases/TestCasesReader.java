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
package io.camunda.process.test.impl.testCases;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.process.test.api.testCases.TestCases;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

/** A reader for CPT test cases defined in JSON format. */
public class TestCasesReader {

  private static final Supplier<ObjectMapper> DEFAULT_OBJECT_MAPPER_SUPPLIER =
      () ->
          new ObjectMapper()
              .registerModules(new Jdk8Module(), new JavaTimeModule())
              .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private final ObjectMapper objectMapper;

  public TestCasesReader() {
    objectMapper = DEFAULT_OBJECT_MAPPER_SUPPLIER.get();
  }

  public TestCasesReader(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Reads test cases from the given input stream.
   *
   * @param stream the input stream to read from
   * @return the test cases
   * @throws RuntimeException if reading the test cases fails
   */
  public TestCases read(final InputStream stream) {
    Objects.requireNonNull(stream, "stream");

    try {
      return objectMapper.readValue(stream, TestCases.class);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to read test cases from stream", e);
    }
  }
}
