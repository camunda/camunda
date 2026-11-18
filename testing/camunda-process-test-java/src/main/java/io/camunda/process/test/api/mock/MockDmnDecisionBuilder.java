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
package io.camunda.process.test.api.mock;

/**
 * Use this interface to build mock DMN decisions in your process tests.
 *
 * <p>Example usage:
 *
 * <pre>
 *   // Mock a decision that returns a fixed output
 *   processTestContext.mockDmnDecision().withDecisionId("my-decision").thenReturn("approved");
 * </pre>
 */
public interface MockDmnDecisionBuilder {

  /**
   * Sets the decision ID of the DMN decision to mock.
   *
   * @param decisionId the ID of the DMN decision to mock
   * @return this builder
   */
  MockDmnDecisionBuilder withDecisionId(String decisionId);

  /**
   * Deploys the mocked DMN decision and sets the provided output as its result.
   *
   * @param decisionOutput the decision's output which may be a value, list or map.
   */
  void thenReturn(Object decisionOutput);
}
