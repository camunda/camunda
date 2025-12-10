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
package io.camunda.process.test.api.dsl;

/** Exception indicating that a test scenario could not be read or parsed. */
public final class TestScenarioReadException extends IllegalArgumentException {

  public TestScenarioReadException(final String message) {
    super(message);
  }

  public TestScenarioReadException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
