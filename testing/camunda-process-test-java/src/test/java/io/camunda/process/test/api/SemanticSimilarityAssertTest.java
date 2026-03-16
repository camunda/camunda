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
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.Variable;
import io.camunda.process.test.api.assertions.ElementSelectors;
import io.camunda.process.test.api.similarity.EmbeddingModelAdapter;
import io.camunda.process.test.api.similarity.SemanticSimilarityConfig;
import io.camunda.process.test.api.similarity.preprocessors.UnicodeNormalizerPreprocessor;
import io.camunda.process.test.api.similarity.preprocessors.WhitespaceNormalizerPreprocessor;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
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
      // given - both expected and actual embed to the same vector → cosine = 1.0 > 0.8
      when(embeddingModel.embed(any())).thenReturn(UNIT_VEC_X);
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(embeddingModel));

      final Variable variable = newVariable("result", "\"Hello, World!\"");
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when / then - passes because cosine(x,x) = 1.0 >= 0.8
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasVariableSimilarTo("result", "Hello there");
    }

    @Test
    void shouldPassWhenScoreEqualsThreshold() {
      // given - exact threshold boundary: use a vector pair giving cosine ≈ 0.8
      // [1,0] · [4,3] / (1 * 5) = 4/5 = 0.8
      // stubs use preprocessed (lowercased) strings since preprocessors run before embed()
      when(embeddingModel.embed("hello there")).thenReturn(new float[] {1.0f, 0.0f});
      when(embeddingModel.embed("\"hello, world!\"")).thenReturn(new float[] {4.0f, 3.0f});
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(embeddingModel));

      final Variable variable = newVariable("result", "\"Hello, World!\"");
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when / then - passes because 0.8 >= 0.8 (inclusive)
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasVariableSimilarTo("result", "Hello there");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWhenEmbeddingsAreOrthogonal() {
      // given - orthogonal vectors → cosine = 0.0 < 0.8
      // stubs use preprocessed (lowercased) strings since preprocessors run before embed()
      when(embeddingModel.embed("hello there")).thenReturn(UNIT_VEC_X);
      when(embeddingModel.embed("\"hello, world!\"")).thenReturn(UNIT_VEC_Y);
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(embeddingModel));

      final Variable variable = newVariable("result", "\"Hello, World!\"");
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when / then - fails because 0.0 < 0.8; message includes score, threshold, and values
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasVariableSimilarTo("result", "Hello there"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("result")
          .hasMessageContaining("0.0")
          .hasMessageContaining("0.80")
          .hasMessageContaining("Hello there")
          .hasMessageContaining("\"Hello, World!\"");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWhenVariableDoesNotExist() {
      // given
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(embeddingModel));

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.emptyList());
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

      // when / then - passes because cosine(x,x) = 1.0 >= 0.8
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasLocalVariableSimilarTo(
              ElementSelectors.byId("task1"), "localVar", "some expectation");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWhenLocalVariableEmbeddingsAreOrthogonal() {
      // given - orthogonal vectors → cosine = 0.0 < 0.8
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
          .hasMessageContaining("0.0")
          .hasMessageContaining("0.80")
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
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when / then — override model always returns identical vectors so similarity == 1.0 (passes)
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .withSemanticSimilarityConfig(
              c -> SemanticSimilarityConfig.defaults().withEmbeddingModelAdapter(overrideModel))
          .hasVariableSimilarTo("result", "Hello");
    }

    @Test
    void shouldSwitchBetweenSimilarityConfigsInSameChain() {
      // given — two models that capture which one was called
      final boolean[] modelACalled = {false};
      final boolean[] modelBCalled = {false};

      final EmbeddingModelAdapter modelA =
          text -> {
            modelACalled[0] = true;
            return UNIT_VEC_X;
          };
      final EmbeddingModelAdapter modelB =
          text -> {
            modelBCalled[0] = true;
            return UNIT_VEC_X;
          };

      final Variable varA = VariableBuilder.newVariable("varA", "\"value A\"");
      final Variable varB = VariableBuilder.newVariable("varB", "\"value B\"");
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(java.util.Arrays.asList(varA, varB));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .withSemanticSimilarityConfig(c -> SemanticSimilarityConfig.of(modelA))
          .hasVariableSimilarTo("varA", "value A")
          .withSemanticSimilarityConfig(c -> SemanticSimilarityConfig.of(modelB))
          .hasVariableSimilarTo("varB", "value B");

      // then
      Assertions.assertThat(modelACalled[0]).isTrue();
      Assertions.assertThat(modelBCalled[0]).isTrue();
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
      // given — global model captures calls, override model also captures
      final boolean[] globalCalled = {false};
      final EmbeddingModelAdapter globalModel =
          text -> {
            globalCalled[0] = true;
            return UNIT_VEC_X;
          };
      final EmbeddingModelAdapter overrideModel = text -> UNIT_VEC_X;
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(globalModel));

      final Variable variable = VariableBuilder.newVariable("result", "\"Hello\"");
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when — use override on first chain
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .withSemanticSimilarityConfig(c -> SemanticSimilarityConfig.of(overrideModel))
          .hasVariableSimilarTo("result", "Hello");

      // then — new assertThat uses global default
      globalCalled[0] = false;
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasVariableSimilarTo("result", "Hello");

      Assertions.assertThat(globalCalled[0]).isTrue();
    }
  }

  @Nested
  class WithPreprocessors {

    @Test
    void shouldApplyDefaultPreprocessorsBeforeEmbedding() {
      // given — capture the strings passed to embed()
      final String[] capturedTexts = new String[2];
      final int[] callCount = {0};
      final EmbeddingModelAdapter capturingModel =
          text -> {
            capturedTexts[callCount[0]++] = text;
            return UNIT_VEC_X;
          };
      CamundaAssert.setSemanticSimilarityConfig(SemanticSimilarityConfig.of(capturingModel));

      final Variable variable = newVariable("result", "  Hello WORLD  ");
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasVariableSimilarTo("result", "  Hello  WORLD  ");

      // then — default preprocessors: unicode → whitespace → lowercase
      Assertions.assertThat(capturedTexts[0]).isEqualTo("hello world"); // expected
      Assertions.assertThat(capturedTexts[1]).isEqualTo("hello world"); // actual
    }

    @Test
    void shouldPassWithAllPreprocessorsDisabledViaWithoutPreprocessors() {
      // given — all preprocessors disabled; stubs use the original unmodified strings
      when(embeddingModel.embed("Hello World")).thenReturn(UNIT_VEC_X);
      when(embeddingModel.embed("  Hello WORLD  ")).thenReturn(UNIT_VEC_X);
      CamundaAssert.setSemanticSimilarityConfig(
          SemanticSimilarityConfig.of(embeddingModel).withoutPreprocessors());

      final Variable variable = newVariable("result", "  Hello WORLD  ");
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when / then — identical vectors even without preprocessing → passes
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasVariableSimilarTo("result", "Hello World");
    }

    @Test
    void shouldApplyCustomPreprocessorPipeline() {
      // given — whitespace only, no lowercase; capture first embed call (expected)
      final String[] capturedExpected = {null};
      final int[] callCount = {0};
      final EmbeddingModelAdapter capturingModel =
          text -> {
            if (callCount[0]++ == 0) {
              capturedExpected[0] = text;
            }
            return UNIT_VEC_X;
          };
      CamundaAssert.setSemanticSimilarityConfig(
          SemanticSimilarityConfig.of(capturingModel)
              .withPreprocessors(new WhitespaceNormalizerPreprocessor()));

      final Variable variable = newVariable("result", "hello");
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasVariableSimilarTo("result", "  Hello  World  ");

      // then — whitespace collapsed but case preserved (lowercase not in pipeline)
      Assertions.assertThat(capturedExpected[0]).isEqualTo("Hello World");
    }

    @Test
    void shouldChainPreprocessorsViaAndThen() {
      // given — unicode + whitespace chained via andThen, no lowercase; capture first embed call
      final String[] capturedExpected = {null};
      final int[] callCount = {0};
      final EmbeddingModelAdapter capturingModel =
          text -> {
            if (callCount[0]++ == 0) {
              capturedExpected[0] = text;
            }
            return UNIT_VEC_X;
          };
      CamundaAssert.setSemanticSimilarityConfig(
          SemanticSimilarityConfig.of(capturingModel)
              .withPreprocessors(
                  new UnicodeNormalizerPreprocessor()
                      .andThen(new WhitespaceNormalizerPreprocessor())));

      final Variable variable = newVariable("result", "hello");
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variable));
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasVariableSimilarTo("result", "  Hello  World  ");

      // then — unicode applied then whitespace collapsed; case preserved
      Assertions.assertThat(capturedExpected[0]).isEqualTo("Hello World");
    }
  }
}
