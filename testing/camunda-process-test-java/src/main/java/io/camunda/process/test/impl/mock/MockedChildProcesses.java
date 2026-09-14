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
package io.camunda.process.test.impl.mock;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The process definitions that tests deployed as a stub of a mocked child process.
 *
 * <p>A key only identifies a stub within the data it was deployed into: once that data is deleted,
 * the runtime hands the key out again, and a later deployment can carry the key of a stub. This
 * record therefore belongs to the runtime holding the data rather than to a single test or test
 * class, both of which the data can outlive.
 */
public final class MockedChildProcesses {

  private final Set<Long> processDefinitionKeys = ConcurrentHashMap.newKeySet();

  /**
   * Records the stub that a test deployed for a mocked child process.
   *
   * @param processDefinitionKey The key of the deployed stub
   */
  public void record(final long processDefinitionKey) {
    processDefinitionKeys.add(processDefinitionKey);
  }

  /**
   * Returns the stubs deployed so far, so that a reader of the data, such as the coverage of a
   * test, tells them apart from the processes the test itself ran.
   *
   * @return The keys of the deployed stubs, as they are now
   */
  public Set<Long> processDefinitionKeys() {
    return new HashSet<>(processDefinitionKeys);
  }

  /** Forgets the stubs recorded so far, once the data they were deployed into is deleted. */
  public void forgetAll() {
    processDefinitionKeys.clear();
  }
}
