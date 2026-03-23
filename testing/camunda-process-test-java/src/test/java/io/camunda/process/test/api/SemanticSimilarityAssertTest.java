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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.Variable;
import io.camunda.process.test.api.assertions.ElementSelectors;
import io.camunda.process.test.api.assertions.VariableSelectors;
import io.camunda.process.test.api.similarity.EmbeddingModelAdapter;
import io.camunda.process.test.api.similarity.SemanticSimilarityConfig;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.similarity.preprocessors.WhitespaceNormalizerPreprocessor;
import io.camunda.process.test.utils.CamundaAssertExpectFailure;
import io.camunda.process.test.utils.CamundaAssertExtension;
import io.camunda.process.test.utils.ElementInstanceBuilder;
import io.camunda.process.test.utils.ProcessInstanceBuilder;
import io.camunda.process.test.utils.VariableBuilder;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({CamundaAssertExtension.class, MockitoExtension.class})
public class SemanticSimilarityAssertTest {

  private static final long PROCESS_INSTANCE_KEY = 1L;
  private static final long ELEMENT_INSTANCE_KEY = 100L;

  /**
   * Returns identical unit vectors — cosine similarity == 1.0 (always passes default threshold).
   */
  private static final float[] UNIT_VEC_X = {1.0f, 0.0f};

  /** Returns an orthogonal vector — cosine similarity == 0.0 (always fails). */
  private static final float[] UNIT_VEC_Y = {0.0f, 1.0f};

  @Mock private CamundaDataSource camundaDataSource;
  @Mock private ProcessInstanceEvent processInstanceEvent;
  @Mock private EmbeddingModelAdapter embeddingModel;

  @BeforeEach
  void configureAssertions() {
    CamundaAssert.initialize(camundaDataSource);
  }

  @BeforeEach
  void configureMocks() {
    // lenient: some tests (e.g. withSimilarityConfig(null)) throw before process instance
    // resolution
    lenient()
        .when(camundaDataSource.findProcessInstances(any()))
        .thenReturn(
            Collections.singletonList(
                ProcessInstanceBuilder.newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));
  }

  @AfterEach
  void resetSimilarityConfig() {
    CamundaAssert.setSemanticSimilarityConfig(null);
  }

  private static Variable newVariable(final String variableName, final String variableValue) {
    return VariableBuilder.newVariable(variableName, variableValue)
        .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
        .build();
  }

  @Nested
  class HasVariableSimilarTo {

    @Test
    void shouldPassWhenEmbeddingsAreIdentical() {
      // given - both expected and actual embed to the same vector → cosine = 1.0 > 0.5 (default
      // threshold)
      when(embeddingModel.embed(any())).thenReturn(UNIT_VEC_X);
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(embeddingModel));

      final Variable variable = newVariable("result", "\"Hello, World!\"");
      when(camundaDataSource.findVariables(any())).thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when / then - passes because cosine(x,x) = 1.0 >= 0.5 (default threshold)
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasVariableSimilarTo(VariableSelectors.byName("result"), "Hello there");
    }

    @Test
    void shouldPassWhenScoreEqualsThreshold() {
      // given - exact threshold boundary: use a vector pair giving cosine = 0.8, set threshold to
      // 0.8 explicitly. [1,0] · [4,3] / (1 * 5) = 4/5 = 0.8
      // stubs use preprocessed (lowercased) strings since preprocessors run before embed()
      when(embeddingModel.embed("hello there")).thenReturn(new float[] {1.0f, 0.0f});
      when(embeddingModel.embed("\"hello, world!\"")).thenReturn(new float[] {4.0f, 3.0f});
      CamundaAssert.setSemanticSimilarityConfig(
          SemanticSimilarityConfig.of(embeddingModel).withThreshold(0.8));

      final Variable variable = newVariable("result", "\"Hello, World!\"");
      when(camundaDataSource.findVariables(any())).thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when / then - passes because 0.8 >= 0.8 (inclusive)
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasVariableSimilarTo("result", "Hello there");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWhenEmbeddingsAreOrthogonal() {
      // given - orthogonal vectors → cosine = 0.0 < 0.5 (default threshold)
      // stubs use preprocessed (lowercased) strings since preprocessors run before embed()
      when(embeddingModel.embed("hello there")).thenReturn(UNIT_VEC_X);
      when(embeddingModel.embed("\"hello, world!\"")).thenReturn(UNIT_VEC_Y);
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(embeddingModel));

      final Variable variable = newVariable("result", "\"Hello, World!\"");
      when(camundaDataSource.findVariables(any())).thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when / then - fails because 0.0 < 0.5; message includes score, threshold, and values
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariableSimilarTo("result", "Hello there"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("variable 'result' did not satisfy similarity check")
          .hasMessageContaining("Expectation: Hello there")
          .hasMessageContaining("Actual value: \"Hello, World!\"")
          .hasMessageContaining("Score: 0.00 (threshold: 0.50)");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWhenVariableDoesNotExist() {
      // given
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(embeddingModel));

      when(camundaDataSource.findVariables(any())).thenReturn(Collections.emptyList());
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariableSimilarTo("missing", "some expectation"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("missing")
          .hasMessageContaining("doesn't exist");
    }

    @Test
    void shouldThrowWhenSimilarityConfigNotSet() {
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariableSimilarTo("result", "some expectation"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("SemanticSimilarityConfig is not set");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldThrowWhenExpectedValueIsNullOrBlank(final String expectedValue) {
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(embeddingModel));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariableSimilarTo("result", expectedValue))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("expectation must not be null or empty");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @CamundaAssertExpectFailure
    void shouldFailWhenActualVariableValueIsNullOrBlank(final String variableValue) {
      // given
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(embeddingModel));

      final Variable variable = newVariable("result", variableValue);
      when(camundaDataSource.findVariables(any())).thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when / then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariableSimilarTo("result", "some expectation"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("result")
          .hasMessageContaining("no value to compare");
    }
  }

  @Nested
  class HasLocalVariableSimilarTo {

    @Test
    void shouldPassWhenLocalVariableEmbeddingsAreIdentical() {
      // given - identical vectors → cosine = 1.0
      when(embeddingModel.embed(any())).thenReturn(UNIT_VEC_X);
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(embeddingModel));

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ElementInstanceBuilder.newActiveElementInstance("task1", PROCESS_INSTANCE_KEY)
                      .setElementInstanceKey(ELEMENT_INSTANCE_KEY)
                      .build()));

      final Variable variable =
          VariableBuilder.newVariable("localVar", "\"local value\"")
              .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
              .setScopeKey(ELEMENT_INSTANCE_KEY)
              .build();
      when(camundaDataSource.findVariables(any())).thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when / then - passes because cosine(x,x) = 1.0 >= 0.5 (default threshold)
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasLocalVariableSimilarTo(
              ElementSelectors.byId("task1"), "localVar", "some expectation");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWhenLocalVariableEmbeddingsAreOrthogonal() {
      // given - orthogonal vectors → cosine = 0.0 < 0.5 (default threshold)
      when(embeddingModel.embed("some expectation")).thenReturn(UNIT_VEC_X);
      when(embeddingModel.embed("\"local value\"")).thenReturn(UNIT_VEC_Y);
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(embeddingModel));

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ElementInstanceBuilder.newActiveElementInstance("task1", PROCESS_INSTANCE_KEY)
                      .setElementInstanceKey(ELEMENT_INSTANCE_KEY)
                      .build()));

      final Variable variable =
          VariableBuilder.newVariable("localVar", "\"local value\"")
              .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
              .setScopeKey(ELEMENT_INSTANCE_KEY)
              .build();
      when(camundaDataSource.findVariables(any())).thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasLocalVariableSimilarTo(
                          ElementSelectors.byId("task1"), "localVar", "some expectation"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("localVar")
          .hasMessageContaining("0.00")
          .hasMessageContaining("0.50")
          .hasMessageContaining("some expectation");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWhenLocalVariableDoesNotExist() {
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(embeddingModel));

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ElementInstanceBuilder.newActiveElementInstance("task1", PROCESS_INSTANCE_KEY)
                      .setElementInstanceKey(ELEMENT_INSTANCE_KEY)
                      .build()));
      when(camundaDataSource.findVariables(any())).thenReturn(Collections.emptyList());
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasLocalVariableSimilarTo(
                          ElementSelectors.byId("task1"), "localVar", "some expectation"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("localVar");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWhenElementInstanceDoesNotExist() {
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(embeddingModel));

      when(camundaDataSource.findElementInstances(any())).thenReturn(Collections.emptyList());
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasLocalVariableSimilarTo(
                          ElementSelectors.byId("nonExistentTask"), "localVar", "some expectation"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("nonExistentTask");
    }

    @Test
    void shouldThrowWhenSimilarityConfigNotSetForLocalVariable() {
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasLocalVariableSimilarTo(
                          ElementSelectors.byId("task1"), "localVar", "some expectation"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("SemanticSimilarityConfig is not set");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @CamundaAssertExpectFailure
    void shouldFailWhenActualLocalVariableValueIsNullOrBlank(final String variableValue) {
      // given
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(embeddingModel));

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ElementInstanceBuilder.newActiveElementInstance("task1", PROCESS_INSTANCE_KEY)
                      .setElementInstanceKey(ELEMENT_INSTANCE_KEY)
                      .build()));

      final Variable variable =
          VariableBuilder.newVariable("localVar", variableValue)
              .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
              .setScopeKey(ELEMENT_INSTANCE_KEY)
              .build();
      when(camundaDataSource.findVariables(any())).thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when / then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasLocalVariableSimilarTo(
                          ElementSelectors.byId("task1"), "localVar", "some expectation"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("localVar")
          .hasMessageContaining("no value to compare");
    }

    @Test
    void shouldPassWhenCalledWithStringElementId() {
      // given - String elementId overload delegates to ElementSelectors.byId(elementId)
      when(embeddingModel.embed(any())).thenReturn(UNIT_VEC_X);
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(embeddingModel));

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ElementInstanceBuilder.newActiveElementInstance("task1", PROCESS_INSTANCE_KEY)
                      .setElementInstanceKey(ELEMENT_INSTANCE_KEY)
                      .build()));

      final Variable variable =
          VariableBuilder.newVariable("localVar", "\"local value\"")
              .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
              .setScopeKey(ELEMENT_INSTANCE_KEY)
              .build();
      when(camundaDataSource.findVariables(any())).thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when / then - passes: String elementId overload resolves to ElementSelectors.byId("task1")
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasLocalVariableSimilarTo("task1", "localVar", "some expectation");
    }
  }

  @Nested
  class WithSimilarityConfig {

    @Test
    void shouldUseOverriddenSimilarityConfig() {
      // given — global model returns orthogonal vectors (fails), override model returns identical
      // vectors (passes)
      final EmbeddingModelAdapter globalModel = text -> UNIT_VEC_Y;
      final EmbeddingModelAdapter overrideModel = text -> UNIT_VEC_X;
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(globalModel));

      final Variable variable = VariableBuilder.newVariable("result", "\"Hello\"");
      when(camundaDataSource.findVariables(any())).thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when / then — override model always returns identical vectors so similarity == 1.0 (passes)
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .withSemanticSimilarityConfig(
              c -> SemanticSimilarityConfig.defaults().withEmbeddingModelAdapter(overrideModel))
          .hasVariableSimilarTo(VariableSelectors.byName("result"), "Hello");
    }

    @Test
    void shouldSwitchBetweenSimilarityConfigsInSameChain() {
      // given
      final EmbeddingModelAdapter modelA = mock(EmbeddingModelAdapter.class);
      when(modelA.embed(any())).thenReturn(UNIT_VEC_X);
      final EmbeddingModelAdapter modelB = mock(EmbeddingModelAdapter.class);
      when(modelB.embed(any())).thenReturn(UNIT_VEC_X);

      final Variable varA = VariableBuilder.newVariable("varA", "value A");
      final Variable varB = VariableBuilder.newVariable("varB", "value B");
      when(camundaDataSource.findVariables(any())).thenReturn(java.util.Arrays.asList(varA, varB));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .withSemanticSimilarityConfig(c -> SemanticSimilarityConfig.of(modelA))
          .hasVariableSimilarTo("varA", "value A")
          .withSemanticSimilarityConfig(c -> SemanticSimilarityConfig.of(modelB))
          .hasVariableSimilarTo("varB", "value B");

      // then
      verify(modelA, times(2)).embed("value a");
      verify(modelB, times(2)).embed("value b");
    }

    @Test
    void shouldThrowWhenSimilarityConfigIsNull() {
      // given
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when / then
      assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .withSemanticSimilarityConfig(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("modifier must not be null");
    }

    @Test
    void shouldNotAffectGlobalDefaultForNewAssertThatCalls() {
      // given
      final EmbeddingModelAdapter globalModel = mock(EmbeddingModelAdapter.class);
      when(globalModel.embed(any())).thenReturn(UNIT_VEC_X);
      final EmbeddingModelAdapter overrideModel = mock(EmbeddingModelAdapter.class);
      when(overrideModel.embed(any())).thenReturn(UNIT_VEC_X);
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(globalModel));

      final Variable variable = VariableBuilder.newVariable("result", "Hi");
      when(camundaDataSource.findVariables(any())).thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when — use override on first chain
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .withSemanticSimilarityConfig(c -> SemanticSimilarityConfig.of(overrideModel))
          .hasVariableSimilarTo("result", "Hello");

      // then — new assertThat uses global default
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasVariableSimilarTo("result", "Hello");

      verify(globalModel).embed("hello");
      verify(globalModel).embed("hi");
      verify(overrideModel).embed("hello");
      verify(overrideModel).embed("hi");
    }
  }

  @Nested
  class WithPreprocessors {

    @Test
    void shouldApplyDefaultPreprocessorsBeforeEmbedding() {
      // given
      final EmbeddingModelAdapter model = mock(EmbeddingModelAdapter.class);
      final ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
      when(model.embed(textCaptor.capture())).thenReturn(UNIT_VEC_X);

      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(model));

      final Variable variable = newVariable("result", "  Hello World \n");
      when(camundaDataSource.findVariables(any())).thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasVariableSimilarTo("result", "  Hello a\u0301 WORLD  ");

      // then — default preprocessors: unicode → whitespace → lowercase
      Assertions.assertThat(textCaptor.getAllValues()).hasSize(2);
      Assertions.assertThat(textCaptor.getAllValues().get(0))
          .isEqualTo("hello \u00E1 world"); // expected
      Assertions.assertThat(textCaptor.getAllValues().get(1)).isEqualTo("hello world"); // actual
    }

    @Test
    void shouldPassWithAllPreprocessorsDisabledViaWithoutPreprocessors() {
      final String variableValue = "  Hello a\u0301 WORLD  ";
      final String expectation = "Hello a\u0301 World";
      // given
      when(embeddingModel.embed(any())).thenReturn(UNIT_VEC_X);
      when(embeddingModel.embed(variableValue)).thenReturn(UNIT_VEC_X);
      CamundaAssert.setSemanticSimilarityConfig(
          SemanticSimilarityConfig.of(embeddingModel).withoutPreprocessors());

      final Variable variable = newVariable("result", variableValue);
      when(camundaDataSource.findVariables(any())).thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when / then — identical vectors even without preprocessing → passes
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasVariableSimilarTo("result", expectation);

      // none of the default preprocessors are applied
      verify(embeddingModel).embed(expectation);
      verify(embeddingModel).embed(variableValue);
    }

    @Test
    void shouldApplyCustomPreprocessorPipeline() {
      // given
      final EmbeddingModelAdapter model = mock(EmbeddingModelAdapter.class);
      final ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
      when(model.embed(textCaptor.capture())).thenReturn(UNIT_VEC_X);

      CamundaAssert.setSemanticSimilarityConfig(
          SemanticSimilarityConfig.of(model)
              .withPreprocessors(WhitespaceNormalizerPreprocessor.INSTANCE));

      final Variable variable = newVariable("result", "hello");
      when(camundaDataSource.findVariables(any())).thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasVariableSimilarTo("result", "  Hello  World  ");

      // then — whitespace collapsed but case preserved (lowercase not in pipeline)
      Assertions.assertThat(textCaptor.getAllValues().get(0)).isEqualTo("Hello World");
    }

    @Test
    void shouldChainPreprocessorsViaAndThen() {
      // given
      final EmbeddingModelAdapter model = mock(EmbeddingModelAdapter.class);
      final ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
      when(model.embed(textCaptor.capture())).thenReturn(UNIT_VEC_X);
      CamundaAssert.setSemanticSimilarityConfig(
          SemanticSimilarityConfig.of(model)
              .withPreprocessors(
                  WhitespaceNormalizerPreprocessor.INSTANCE.andThen(
                      text -> text.replaceAll("Hello", " Hi"))));

      final Variable variable = newVariable("result", "hello");
      when(camundaDataSource.findVariables(any())).thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasVariableSimilarTo("result", "  Hello  World  ");

      // then — whitespace collapsed then text replaced; case preserved
      Assertions.assertThat(textCaptor.getAllValues().get(0)).isEqualTo(" Hi World");
    }
  }
}
