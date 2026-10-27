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
package io.camunda.process.test.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.Variable;
import io.camunda.process.test.api.assertions.DecisionSelectors;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.utils.CamundaAssertExpectFailure;
import io.camunda.process.test.utils.CamundaAssertExtension;
import io.camunda.process.test.utils.Jackson3JsonMapper;
import io.camunda.process.test.utils.ProcessInstanceBuilder;
import io.camunda.process.test.utils.VariableBuilder;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies that the assertions work with a {@link io.camunda.client.api.JsonMapper} that is not
 * built on Jackson 2.
 *
 * <p>The {@link io.camunda.client.api.JsonMapper} contract has no notion of a JSON tree. Requiring
 * one from the client's mapper restricted the assertions to Jackson 2 based mappers and broke the
 * client's own default on Spring Boot 4, which uses Jackson 3.
 *
 * <p>Both conversion directions are covered because they fail independently: the variable
 * assertions convert the <em>expected</em> value first, while {@link
 * io.camunda.process.test.impl.assertions.DecisionOutputAssertj} reads the <em>actual</em> value
 * first. Covering only the variable assertions would let a fix that repairs just the expected-value
 * path look complete.
 */
@ExtendWith({CamundaAssertExtension.class, MockitoExtension.class})
public class JsonMapperAssertTest {

  private static final long PROCESS_INSTANCE_KEY = 1L;
  private static final String DECISION_NAME = "decision";
  private static final String DECISION_INSTANCE_ID = "decision-instance-id";

  @Mock private CamundaDataSource camundaDataSource;

  @BeforeEach
  void configureAssertions() {
    CamundaAssert.initialize(camundaDataSource);
    CamundaAssert.setJsonMapper(new Jackson3JsonMapper());
  }

  @AfterEach
  void resetJsonMapper() {
    CamundaAssert.setJsonMapper(CamundaAssert.DEFAULT_JSON_MAPPER);
  }

  @Test
  void shouldHaveVariable() {
    // given
    mockProcessInstance();
    when(camundaDataSource.findVariables(any()))
        .thenReturn(Collections.singletonList(newVariable("isRefund", "true")));

    // when/then
    CamundaAssert.assertThatProcessInstance(ProcessInstanceSelectors.byKey(PROCESS_INSTANCE_KEY))
        .hasVariable("isRefund", true);
  }

  @Test
  void shouldHaveVariables() {
    // given
    mockProcessInstance();
    final List<Variable> variables =
        Arrays.asList(
            newVariable("isRefund", "true"),
            newVariable("amount", "10"),
            newVariable("currency", "\"EUR\""),
            newVariable("order", "{\"id\":123}"));
    when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(anyLong()))
        .thenReturn(variables);

    final Map<String, Object> expectedVariables = new LinkedHashMap<>();
    expectedVariables.put("isRefund", true);
    expectedVariables.put("amount", 10);
    expectedVariables.put("currency", "EUR");
    expectedVariables.put("order", Collections.singletonMap("id", 123));

    // when/then
    CamundaAssert.assertThatProcessInstance(ProcessInstanceSelectors.byKey(PROCESS_INSTANCE_KEY))
        .hasVariables(expectedVariables);
  }

  @Test
  @CamundaAssertExpectFailure
  void shouldReportMismatchedVariablesAsJson() {
    // given
    mockProcessInstance();
    when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(anyLong()))
        .thenReturn(Collections.singletonList(newVariable("isRefund", "false")));

    // when
    Assertions.assertThatThrownBy(
            () ->
                CamundaAssert.assertThatProcessInstance(
                        ProcessInstanceSelectors.byKey(PROCESS_INSTANCE_KEY))
                    .hasVariables(Collections.singletonMap("isRefund", true)))
        // then
        .isInstanceOf(AssertionError.class)
        .hasMessage(
            "Process instance [key: %d] should have the variables {\"isRefund\":true} but was {\"isRefund\":false}.",
            PROCESS_INSTANCE_KEY);
  }

  @Test
  void shouldHaveDecisionOutput() {
    // given
    final DecisionInstance decisionInstance = newDecisionInstance("\"approved\"");
    when(camundaDataSource.findDecisionInstances(any()))
        .thenReturn(Collections.singletonList(decisionInstance));
    when(camundaDataSource.getDecisionInstance(DECISION_INSTANCE_ID)).thenReturn(decisionInstance);

    // when/then
    CamundaAssert.assertThatDecision(DecisionSelectors.byName(DECISION_NAME)).hasOutput("approved");
  }

  private void mockProcessInstance() {
    when(camundaDataSource.findProcessInstances(any()))
        .thenReturn(
            Collections.singletonList(
                ProcessInstanceBuilder.newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));
  }

  private static Variable newVariable(final String variableName, final String variableValue) {
    return VariableBuilder.newVariable(variableName, variableValue)
        .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
        .build();
  }

  private static DecisionInstance newDecisionInstance(final String result) {
    final DecisionInstance decisionInstance = mock(DecisionInstance.class);
    when(decisionInstance.getDecisionDefinitionName()).thenReturn(DECISION_NAME);
    when(decisionInstance.getDecisionInstanceId()).thenReturn(DECISION_INSTANCE_ID);
    when(decisionInstance.getResult()).thenReturn(result);
    return decisionInstance;
  }
}
