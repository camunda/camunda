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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import java.util.List;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.validation.ValidationResults;
import org.junit.Test;

public class CompositeValidationVisitorTest {

  @Test
  public void shouldDelegateVisitToAllVisitors() {
    // given - mock visitors
    final ValidationVisitor visitor1 = mock(ValidationVisitor.class);
    final ValidationVisitor visitor2 = mock(ValidationVisitor.class);
    final ValidationVisitor visitor3 = mock(ValidationVisitor.class);

    final CompositeValidationVisitor compositeVisitor =
        new CompositeValidationVisitor(visitor1, visitor2, visitor3);

    compositeVisitor.visit(mock(ModelElementType.class), mock(BpmnModelElementInstance.class));

    // then - all visitors should have been called with visit()
    verify(visitor1).visit(any(ModelElementType.class), any(BpmnModelElementInstance.class));
    verify(visitor2).visit(any(ModelElementType.class), any(BpmnModelElementInstance.class));
    verify(visitor3).visit(any(ModelElementType.class), any(BpmnModelElementInstance.class));
  }

  @Test
  public void shouldCollectValidationResultsFromAllVisitors() {
    // given - mock visitors with different results
    final ValidationVisitor visitor1 = mock(ValidationVisitor.class);
    final ValidationVisitor visitor2 = mock(ValidationVisitor.class);

    final ValidationResults results1 = createValidationResults(true);
    final ValidationResults results2 = createValidationResults(false);

    when(visitor1.getValidationResult()).thenReturn(results1);
    when(visitor2.getValidationResult()).thenReturn(results2);

    final CompositeValidationVisitor compositeVisitor =
        new CompositeValidationVisitor(visitor1, visitor2);

    // when
    final List<ValidationResults> results = compositeVisitor.getValidationResults();

    // then
    assertThat(results).hasSize(2);
    assertThat(results.get(0).hasErrors()).isTrue();
    assertThat(results.get(1).hasErrors()).isFalse();
  }

  @Test
  public void shouldResetAllVisitors() {
    // given - mock visitors
    final ValidationVisitor visitor1 = mock(ValidationVisitor.class);
    final ValidationVisitor visitor2 = mock(ValidationVisitor.class);
    final ValidationVisitor visitor3 = mock(ValidationVisitor.class);

    final CompositeValidationVisitor compositeVisitor =
        new CompositeValidationVisitor(visitor1, visitor2, visitor3);

    // when
    compositeVisitor.reset();

    // then - reset should have been called on all visitors
    verify(visitor1, times(1)).reset();
    verify(visitor2, times(1)).reset();
    verify(visitor3, times(1)).reset();
  }

  private ValidationResults createValidationResults(final boolean hasErrors) {
    final ValidationResults results = mock(ValidationResults.class);
    when(results.hasErrors()).thenReturn(hasErrors);
    if (hasErrors) {
      when(results.getErrorCount()).thenReturn(1);
    } else {
      when(results.getErrorCount()).thenReturn(0);
    }

    return results;
  }
}
