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
package io.camunda.spring.client.properties;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.spring.client.annotation.JobWorker;
import io.camunda.spring.client.annotation.Variable;
import io.camunda.spring.client.annotation.VariablesAsType;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.bean.ClassInfo;
import io.camunda.spring.client.bean.MethodInfo;
import io.camunda.spring.client.jobhandling.SpringBeanJobHandlerFactory;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

public class PropertyBasedJobWorkerValueCustomizerTest {

  private static MethodInfo methodInfo(
      final Object bean, final String beanName, final String methodName) {
    try {
      return MethodInfo.builder()
          .classInfo(ClassInfo.builder().beanName(beanName).bean(bean).build())
          .method(
              Arrays.stream(PropertyBasedJobWorkerValueCustomizerTest.class.getDeclaredMethods())
                  .filter(m -> m.getName().equals(methodName))
                  .findFirst()
                  .orElseThrow(
                      () -> new IllegalStateException("No method present with name " + methodName)))
          .build();
    } catch (final Exception e) {
      throw new RuntimeException("Error while constructing methodInfo for method " + methodName, e);
    }
  }

  @Test
  void shouldApplyDistinctFetchVariables() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setFetchVariables(List.of("a", "b", "c", "a", "b", "c"));
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "emptyWorker")));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getFetchVariables()).containsExactly("a", "b", "c");
  }

  private static CamundaClientProperties properties() {
    return new CamundaClientProperties();
  }

  @JobWorker
  void sampleWorker(
      @Variable final String var1, @VariablesAsType final ComplexProcessVariable var2) {}

  @JobWorker
  void activatedJobWorker(@Variable final String var1, final ActivatedJob activatedJob) {}

  @JobWorker
  void sampleWorkerWithJsonProperty(@VariablesAsType final PropertyAnnotatedClass annotatedClass) {}

  @JobWorker
  void sampleWorkerWithEmptyJsonProperty(
      @VariablesAsType final PropertyAnnotatedClassEmptyValue annotatedClass) {}

  @JobWorker
  void emptyWorker() {}

  @JobWorker(tenantIds = {"tenant1", "tenant2"})
  void customTenantWorker() {}

  @Test
  void shouldNotAdjustVariableFilterVariablesAsActivatedJobIsInjected() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setFetchVariables(List.of("a", "var1", "b"));
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "activatedJobWorker")));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getFetchVariables()).containsExactly("a", "var1", "b");
  }

  @Test
  void shouldSetDefaultName() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setName("defaultName");
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "sampleWorker")));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getName()).isEqualTo("defaultName");
  }

  @Test
  void shouldSetGeneratedName() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "sampleWorker")));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getName()).isEqualTo("testBean#sampleWorker");
  }

  @Test
  void shouldSetDefaultType() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setType("defaultType");
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "sampleWorker")));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getType()).isEqualTo("defaultType");
  }

  @Test
  void shouldSetGeneratedType() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "sampleWorker")));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getType()).isEqualTo("sampleWorker");
  }

  @Test
  void shouldSetVariablesFromVariableAnnotation() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "sampleWorker")));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getFetchVariables()).contains("var1");
  }

  @Test
  void shouldSetVariablesFromVariablesAsTypeAnnotation() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "sampleWorker")));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getFetchVariables()).contains("var3", "var4");
  }

  @Test
  void shouldNotSetNameOfVariablesAsTypeAnnotatedField() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "sampleWorker")));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getFetchVariables()).doesNotContain("var2");
  }

  @Test
  void shouldApplyOverrides() {
    // given
    final CamundaClientProperties properties = properties();
    final CamundaClientJobWorkerProperties override = new CamundaClientJobWorkerProperties();
    override.setEnabled(false);
    properties.getWorker().getOverride().put("sampleWorker", override);
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "sampleWorker")));
    assertThat(jobWorkerValue.getEnabled()).isNull();
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getEnabled()).isFalse();
  }

  @Test
  void shouldApplyGlobalOverride() {
    // given
    final CamundaClientProperties properties = properties();
    final CamundaClientJobWorkerProperties override = properties.getWorker().getDefaults();
    override.setEnabled(false);
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "sampleWorker")));
    assertThat(jobWorkerValue.getEnabled()).isNull();
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getEnabled()).isFalse();
  }

  @Test
  void shouldApplyWorkerOverridesOverGlobalOverrides() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setEnabled(true);
    final CamundaClientJobWorkerProperties override = new CamundaClientJobWorkerProperties();
    override.setEnabled(false);
    properties.getWorker().getOverride().put("sampleWorker", override);
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "sampleWorker")));
    assertThat(jobWorkerValue.getEnabled()).isNull();
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getEnabled()).isFalse();
  }

  @Test
  void shouldApplyPropertyAnnotationOnVariableFiltering() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(
            methodInfo(this, "testBean", "sampleWorkerWithJsonProperty")));

    // when
    customizer.customize(jobWorkerValue);

    // then
    assertThat(jobWorkerValue.getFetchVariables()).containsExactly("some_name");
  }

  @Test
  void shouldNotOverrideTypeAndNameAndFetchVariablesFromGlobalsIfSet() {
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setType("globalOverride");
    properties.getWorker().getDefaults().setName("globalName");
    properties.getWorker().getDefaults().setFetchVariables(List.of("overrideVariable"));
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(
            methodInfo(this, "testBean", "sampleWorkerWithJsonProperty")));
    jobWorkerValue.setType("initialValue");
    jobWorkerValue.setName("someName");
    jobWorkerValue.setFetchVariables(List.of("initialVariable"));
    customizer.customize(jobWorkerValue);
    assertThat(jobWorkerValue.getType()).isEqualTo("initialValue");
    assertThat(jobWorkerValue.getName()).isEqualTo("someName");
    assertThat(jobWorkerValue.getFetchVariables()).contains("initialVariable");
  }

  @Test
  void shouldOverrideTypeAndNameFromGlobalsIfNotSet() {
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setType("globalOverride");
    properties.getWorker().getDefaults().setName("globalName");
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(
            methodInfo(this, "testBean", "sampleWorkerWithJsonProperty")));
    customizer.customize(jobWorkerValue);
    assertThat(jobWorkerValue.getType()).isEqualTo("globalOverride");
    assertThat(jobWorkerValue.getName()).isEqualTo("globalName");
  }

  @Test
  void shouldOverrideTypeAndNameAndFetchVariablesFromLocalsIfSet() {
    final CamundaClientProperties properties = properties();
    final CamundaClientJobWorkerProperties override = new CamundaClientJobWorkerProperties();
    override.setType("localOverride");
    override.setName("localName");
    override.setFetchVariables(List.of("overrideVariable"));
    properties.getWorker().getOverride().put("initialValue", override);
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(
            methodInfo(this, "testBean", "sampleWorkerWithJsonProperty")));
    jobWorkerValue.setType("initialValue");
    jobWorkerValue.setName("someName");
    jobWorkerValue.setFetchVariables(List.of("initialVariable"));
    customizer.customize(jobWorkerValue);
    assertThat(jobWorkerValue.getType()).isEqualTo("localOverride");
    assertThat(jobWorkerValue.getName()).isEqualTo("localName");
    assertThat(jobWorkerValue.getFetchVariables()).contains("overrideVariable");
  }

  private Stream<Input<Object>> generateInput() {
    return Stream.of(
            //    private String type;
            new Input<>(
                "type",
                CamundaClientJobWorkerProperties::setType,
                JobWorkerValue::getType,
                "testType"),
            //    private String name;
            new Input<>(
                "name",
                CamundaClientJobWorkerProperties::setName,
                JobWorkerValue::getName,
                "testName"),
            //    private Duration timeout;
            new Input<>(
                "timeout",
                CamundaClientJobWorkerProperties::setTimeout,
                JobWorkerValue::getTimeout,
                Duration.ofSeconds(60)),
            //    private Integer maxJobsActive;
            new Input<>(
                "maxJobsActive",
                CamundaClientJobWorkerProperties::setMaxJobsActive,
                JobWorkerValue::getMaxJobsActive,
                12),
            //    private Duration requestTimeout;
            new Input<>(
                "requestTimeout",
                CamundaClientJobWorkerProperties::setRequestTimeout,
                JobWorkerValue::getRequestTimeout,
                Duration.ofSeconds(70)),
            //    private Duration pollInterval;
            new Input<>(
                "pollInterval",
                CamundaClientJobWorkerProperties::setPollInterval,
                JobWorkerValue::getPollInterval,
                Duration.ofSeconds(80)),
            //    private Boolean autoComplete;
            new Input<>(
                "autoComplete",
                CamundaClientJobWorkerProperties::setAutoComplete,
                JobWorkerValue::getAutoComplete,
                true),
            //    private List<String> fetchVariables;
            new Input<>(
                "fetchVariables",
                CamundaClientJobWorkerProperties::setFetchVariables,
                JobWorkerValue::getFetchVariables,
                List.of("var1", "var2", "var3")),
            //    private Boolean enabled;
            new Input<>(
                "enabled",
                CamundaClientJobWorkerProperties::setEnabled,
                JobWorkerValue::getEnabled,
                true),
            //    private List<String> tenantIds;
            new Input<>(
                "tenantIds",
                CamundaClientJobWorkerProperties::setTenantIds,
                JobWorkerValue::getTenantIds,
                List.of("tenant1", "tenant2", "tenant3")),
            //    private Boolean forceFetchAllVariables;
            new Input<>(
                "forceFetchAllVariables",
                CamundaClientJobWorkerProperties::setForceFetchAllVariables,
                JobWorkerValue::getForceFetchAllVariables,
                false),
            //    private Boolean streamEnabled;
            new Input<>(
                "streamEnabled",
                CamundaClientJobWorkerProperties::setStreamEnabled,
                JobWorkerValue::getStreamEnabled,
                true),
            //    private Duration streamTimeout;
            new Input<>(
                "streamTimeout",
                CamundaClientJobWorkerProperties::setStreamTimeout,
                JobWorkerValue::getStreamTimeout,
                Duration.ofSeconds(30)),
            //    private Integer maxRetries;
            new Input<>(
                "maxRetries",
                CamundaClientJobWorkerProperties::setMaxRetries,
                JobWorkerValue::getMaxRetries,
                7))
        .map(
            i ->
                new Input<Object>(
                    i.displayName(),
                    (BiConsumer<CamundaClientJobWorkerProperties, Object>) i.setter(),
                    i.getter(),
                    i.expected()));
  }

  @TestFactory
  Stream<DynamicTest> shouldSetGlobalProperties() {
    return DynamicTest.stream(
        generateInput(), i -> shouldSetGlobalProperty(i.setter(), i.getter(), i.expected()));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void shouldSetGlobalProperty(
      final BiConsumer<CamundaClientJobWorkerProperties, Object> setter,
      final Function<JobWorkerValue, Object> getter,
      final Object expected) {
    final CamundaClientProperties properties = properties();
    setter.accept(properties.getWorker().getDefaults(), expected);
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "emptyWorker")));
    customizer.customize(jobWorkerValue);
    final Object result = getter.apply(jobWorkerValue);
    if (result instanceof Collection<?>) {
      assertThat((Collection<?>) result).containsExactlyInAnyOrderElementsOf((Iterable) expected);
    } else {
      assertThat(result).isEqualTo(expected);
    }
  }

  @Test
  void shouldNotApplyPropertyAnnotationOnEmptyValue() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(
            methodInfo(this, "testBean", "sampleWorkerWithEmptyJsonProperty")));

    // when
    customizer.customize(jobWorkerValue);

    // then
    assertThat(jobWorkerValue.getFetchVariables()).containsExactly("value");
  }

  @Test
  void shouldApplyWorkerDefaultTenantIds() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setTenantIds(List.of("customTenantId"));

    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);

    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "sampleWorker")));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds()).contains("customTenantId");
  }

  @Test
  void shouldMergeWorkerDefaultTenantIdsAndWorkerAnnotationTenantIds() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setTenantIds(List.of("customTenantId"));

    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);

    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "sampleWorker")));
    jobWorkerValue.setTenantIds(List.of("annotationTenantId"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds())
        .containsExactlyInAnyOrder("annotationTenantId", "customTenantId");
  }

  @Test
  void shouldApplyClientDefaultTenantIdWhenNothingElseConfigured() {
    // given
    final CamundaClientProperties properties = properties();
    properties.setTenantId("testTenantId");
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "sampleWorker")));
    customizer.customize(jobWorkerValue);
    assertThat(jobWorkerValue.getTenantIds()).containsOnly("testTenantId");
  }

  @Test
  void shouldApplyWorkerDefaultTenantIdsOnlyWhenClientDefaultTenantIdIsSet() {
    // given
    final CamundaClientProperties properties = properties();
    properties.setTenantId("customTenantId");
    properties.getWorker().getDefaults().setTenantIds(List.of("testTenantId1", "testTenantId2"));

    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);

    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "sampleWorker")));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds())
        .containsExactlyInAnyOrder("testTenantId1", "testTenantId2");
  }

  @Test
  void shouldMergeClientDefaultTenantIdsAndWorkerAnnotationTenantIdsWhenNothingElseConfigured() {
    // given
    final CamundaClientProperties properties = properties();
    properties.setTenantId("customTenantId");

    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);

    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "sampleWorker")));
    jobWorkerValue.setTenantIds(List.of("annotationTenantId"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds())
        .containsExactlyInAnyOrder("annotationTenantId", "customTenantId");
  }

  @Test
  void shouldApplyTenantIdWorkerOverridesRegardlessOfDefaultsSet() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setTenantIds(List.of("workerDefaultsId"));
    properties.setTenantId("clientDefaultId");
    final CamundaClientJobWorkerProperties overrideJobWorkerValue =
        new CamundaClientJobWorkerProperties();
    overrideJobWorkerValue.setTenantIds(List.of("overriddenTenantId"));
    properties.getWorker().getOverride().put("sampleWorker", overrideJobWorkerValue);

    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);

    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setTenantIds(List.of("annotationWorkerDefaultsId"));
    jobWorkerValue.setJobHandlerFactory(
        new SpringBeanJobHandlerFactory(methodInfo(this, "testBean", "sampleWorker")));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds()).contains("overriddenTenantId");
  }

  private record Input<T extends Object>(
      String displayName,
      BiConsumer<CamundaClientJobWorkerProperties, T> setter,
      Function<JobWorkerValue, Object> getter,
      T expected)
      implements Named<Input<T>> {

    @Override
    public String getName() {
      return displayName;
    }

    @Override
    public Input<T> getPayload() {
      return this;
    }
  }

  private static final class ComplexProcessVariable {
    private String var3;
    private String var4;

    public String getVar3() {
      return var3;
    }

    public void setVar3(final String var3) {
      this.var3 = var3;
    }

    public String getVar4() {
      return var4;
    }

    public void setVar4(final String var4) {
      this.var4 = var4;
    }
  }

  private static final class PropertyAnnotatedClass {
    @JsonProperty("some_name")
    private String value;
  }

  private static final class PropertyAnnotatedClassEmptyValue {
    @JsonProperty() private String value;
  }
}
