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
package io.camunda.zeebe.model.bpmn.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.traversal.ModelWalker;
import java.util.Collections;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;
import org.junit.jupiter.api.Test;

final class ValidationVisitorTest {

  @Test
  void shouldNotKeepStatefulValidatorStateAfterReset() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task1", b -> b.zeebeJobType("type"))
            .serviceTask("task2", b -> b.zeebeJobType("type"))
            .endEvent()
            .done();
    final ValidationVisitor visitor =
        new ValidationVisitor(
            ValidationVisitor.groupByType(Collections.emptyList()),
            () -> Collections.<ModelElementValidator<?>>singletonList(new RejectSecondTask()));
    new ModelWalker(model).walk(visitor);
    assertThat(visitor.getValidationResult().getErrorCount()).isEqualTo(1);

    // when
    visitor.reset();
    new ModelWalker(model).walk(visitor);

    // then
    assertThat(visitor.getValidationResult().getErrorCount()).isEqualTo(1);
  }

  /** Reports an error for every task after the first, so it depends on its own state. */
  private static final class RejectSecondTask implements ModelElementValidator<ServiceTask> {

    private int seen;

    @Override
    public Class<ServiceTask> getElementType() {
      return ServiceTask.class;
    }

    @Override
    public void validate(final ServiceTask element, final ValidationResultCollector collector) {
      if (seen++ > 0) {
        collector.addError(0, "second task");
      }
    }
  }
}
