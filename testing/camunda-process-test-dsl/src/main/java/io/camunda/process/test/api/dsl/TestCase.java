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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

/** A test case with instructions to validate the business process. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableTestCase.Builder.class)
public interface TestCase {

  /**
   * The name of the test case.
   *
   * @return the name
   */
  String getName();

  /**
   * The description of the test case.
   *
   * @return the description
   */
  Optional<String> getDescription();

  /**
   * The instructions of the test case.
   *
   * @return the instructions
   */
  List<TestCaseInstruction> getInstructions();
}
