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
package io.camunda.process.test.impl.assertions;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.process.test.api.assertions.EvaluateDecisionResponseAssert;
import org.assertj.core.api.AbstractAssert;

public class EvaluateDecisionResponseAssertj
    extends AbstractAssert<EvaluateDecisionResponseAssertj, EvaluateDecisionResponse>
    implements EvaluateDecisionResponseAssert {

  private final DecisionOutputAssertj decisionOutputAssertj;

  public EvaluateDecisionResponseAssertj(final EvaluateDecisionResponse evaluateDecisionResponse) {
    super(evaluateDecisionResponse, EvaluateDecisionResponseAssert.class);

    this.decisionOutputAssertj =
        new DecisionOutputAssertj(
            String.format(
                "Expected EvaluateDecisionResponse [%s]",
                evaluateDecisionResponse.getDecisionId()));
  }

  @Override
  public EvaluateDecisionResponseAssert isEvaluated() {
    assertThat(actual.getFailureMessage())
        .withFailMessage(
            "EvaluateDecisionResponse [%s] failed with message: %s",
            actual.getDecisionId(), actual.getFailureMessage())
        .isNullOrEmpty();

    return this;
  }

  @Override
  public EvaluateDecisionResponseAssert hasOutput(final Object expectedOutput) {
    decisionOutputAssertj.hasOutput(actual.getDecisionOutput(), expectedOutput);
    return this;
  }
}
