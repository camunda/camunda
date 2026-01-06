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
package io.camunda.process.test.impl.extension;

import io.camunda.process.test.api.dsl.TestCase;
import io.camunda.process.test.api.dsl.TestCaseInstruction;
import java.util.List;
import java.util.Optional;

/**
 * A test case delegate with a modified toString implementation that returns only the name of the
 * test case.
 */
public final class SourceTestCase implements TestCase {

  private final TestCase delegate;

  public SourceTestCase(final TestCase delegate) {
    this.delegate = delegate;
  }

  @Override
  public String getName() {
    return delegate.getName();
  }

  @Override
  public Optional<String> getDescription() {
    return delegate.getDescription();
  }

  @Override
  public List<TestCaseInstruction> getInstructions() {
    return delegate.getInstructions();
  }

  @Override
  public String toString() {
    return getName();
  }
}
