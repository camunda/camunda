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
package io.camunda.process.test.impl.spec.dsl.verifications;

import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.impl.spec.SpecTestContext;
import io.camunda.process.test.impl.spec.dsl.AbstractSpecInstruction;
import io.camunda.process.test.impl.spec.dsl.SpecVerification;

public class ProcessInstanceStateVerification extends AbstractSpecInstruction
    implements SpecVerification {

  private String processInstanceAlias;
  private ProcessInstanceState state;

  public String getProcessInstanceAlias() {
    return processInstanceAlias;
  }

  public void setProcessInstanceAlias(final String processInstanceAlias) {
    this.processInstanceAlias = processInstanceAlias;
  }

  public ProcessInstanceState getState() {
    return state;
  }

  public void setState(final ProcessInstanceState state) {
    this.state = state;
  }

  @Override
  public void verify(
      final SpecTestContext testContext, final CamundaProcessTestContext processTestContext)
      throws AssertionError {

    if (state == ProcessInstanceState.ACTIVE) {
      testContext.assertThatProcessInstance(processInstanceAlias).isActive();

    } else if (state == ProcessInstanceState.COMPLETED) {
      testContext.assertThatProcessInstance(processInstanceAlias).isCompleted();

    } else if (state == ProcessInstanceState.TERMINATED) {
      testContext.assertThatProcessInstance(processInstanceAlias).isTerminated();
    }
  }
}
