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
package io.camunda.client.spring.properties;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.annotation.VariablesAsType;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware.Empty;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware.FromAnnotation;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware.FromDefaultProperty;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware.FromOverrideProperty;
import io.camunda.client.annotation.value.JobWorkerValue.SourceAware.GeneratedFromMethodInfo;
import io.camunda.client.api.response.ActivatedJob;
import java.time.Duration;
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

  @Test
  void shouldApplyDefaults() {
    // given
    final CamundaClientProperties properties = properties();
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    customizer.customize(jobWorkerValue);
    assertThat(jobWorkerValue.getEnabled()).isEqualTo(new FromDefaultProperty<>(true));
    assertThat(jobWorkerValue.getAutoComplete()).isEqualTo(new FromDefaultProperty<>(true));
    assertThat(jobWorkerValue.getMaxRetries()).isEqualTo(new FromDefaultProperty<>(0));
  }

  @Test
  void shouldApplyAnnotationValueOverDefaultProperty() {
    // given
    final CamundaClientProperties properties = properties();
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setMaxJobsActive(new FromAnnotation<>(1));
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getMaxJobsActive()).isEqualTo(new FromAnnotation<>(1));
  }

  @Test
  void shouldApplyDistinctFetchVariables() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setFetchVariables(List.of("a", "b", "c", "a", "b", "c"));
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getFetchVariables())
        .containsExactly(
            new FromDefaultProperty<>("a"),
            new FromDefaultProperty<>("b"),
            new FromDefaultProperty<>("c"));
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
    jobWorkerValue.setFetchVariables(
        List.of(
            new FromDefaultProperty<>("a"),
            new FromDefaultProperty<>("var1"),
            new FromDefaultProperty<>("b")));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getFetchVariables())
        .containsExactly(
            new FromDefaultProperty<>("a"),
            new FromDefaultProperty<>("var1"),
            new FromDefaultProperty<>("b"));
  }

  @Test
  void shouldSetDefaultName() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setName("defaultName");
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getName().value()).isEqualTo("defaultName");
  }

  @Test
  void shouldSetGeneratedName() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setName(new GeneratedFromMethodInfo<>("testBean#sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getName())
        .isEqualTo(new GeneratedFromMethodInfo<>("testBean#sampleWorker"));
  }

  @Test
  void shouldSetDefaultType() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setType("defaultType");
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getType().value()).isEqualTo("defaultType");
  }

  @Test
  void shouldSetGeneratedType() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setType(new GeneratedFromMethodInfo<>("sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getType()).isEqualTo(new GeneratedFromMethodInfo<>("sampleWorker"));
  }

  @Test
  void shouldNotSetNameOfVariablesAsTypeAnnotatedField() {
    // given
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties());
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getFetchVariables().stream().map(SourceAware::value).toList())
        .doesNotContain("var2");
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
    jobWorkerValue.setType(new GeneratedFromMethodInfo<>("sampleWorker"));
    assertThat(jobWorkerValue.getEnabled()).isEqualTo(new Empty<>());
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getEnabled()).isEqualTo(new FromOverrideProperty<>(false));
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
    assertThat(jobWorkerValue.getEnabled()).isEqualTo(new Empty<>());
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getEnabled()).isEqualTo(new FromDefaultProperty<>(false));
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
    jobWorkerValue.setType(new GeneratedFromMethodInfo<>("sampleWorker"));
    assertThat(jobWorkerValue.getEnabled()).isEqualTo(new Empty<>());
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getEnabled()).isEqualTo(new FromOverrideProperty<>(false));
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
    jobWorkerValue.setType(new FromAnnotation<>("initialValue"));
    jobWorkerValue.setName(new FromAnnotation<>("someName"));
    jobWorkerValue.setFetchVariables(List.of(new FromAnnotation<>("initialVariable")));
    customizer.customize(jobWorkerValue);
    assertThat(jobWorkerValue.getType().value()).isEqualTo("initialValue");
    assertThat(jobWorkerValue.getName().value()).isEqualTo("someName");
    assertThat(jobWorkerValue.getFetchVariables())
        .contains(new FromAnnotation<>("initialVariable"));
  }

  @Test
  void shouldOverrideTypeAndNameFromGlobalsIfNotSet() {
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setType("globalOverride");
    properties.getWorker().getDefaults().setName("globalName");
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    customizer.customize(jobWorkerValue);
    assertThat(jobWorkerValue.getType().value()).isEqualTo("globalOverride");
    assertThat(jobWorkerValue.getName().value()).isEqualTo("globalName");
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
    jobWorkerValue.setType(new FromAnnotation<>("initialValue"));
    jobWorkerValue.setName(new FromAnnotation<>("someName"));
    jobWorkerValue.setFetchVariables(List.of(new FromAnnotation<>("initialVariable")));
    customizer.customize(jobWorkerValue);
    assertThat(jobWorkerValue.getType()).isEqualTo(new FromOverrideProperty<>("localOverride"));
    assertThat(jobWorkerValue.getName()).isEqualTo(new FromOverrideProperty<>("localName"));
    assertThat(jobWorkerValue.getFetchVariables())
        .contains(new FromOverrideProperty<>("overrideVariable"));
  }

  private Stream<Input<Object>> generateInput() {
    return Stream.of(
            //    private String type;
            new Input<>(
                "type",
                CamundaClientJobWorkerProperties::setType,
                j -> j.getType().value(),
                "testType"),
            //    private String name;
            new Input<>(
                "name",
                CamundaClientJobWorkerProperties::setName,
                j -> j.getName().value(),
                "testName"),
            //    private Duration timeout;
            new Input<>(
                "timeout",
                CamundaClientJobWorkerProperties::setTimeout,
                j -> j.getTimeout().value(),
                Duration.ofSeconds(60)),
            //    private Integer maxJobsActive;
            new Input<>(
                "maxJobsActive",
                CamundaClientJobWorkerProperties::setMaxJobsActive,
                j -> j.getMaxJobsActive().value(),
                12),
            //    private Duration requestTimeout;
            new Input<>(
                "requestTimeout",
                CamundaClientJobWorkerProperties::setRequestTimeout,
                j -> j.getRequestTimeout().value(),
                Duration.ofSeconds(70)),
            //    private Duration pollInterval;
            new Input<>(
                "pollInterval",
                CamundaClientJobWorkerProperties::setPollInterval,
                j -> j.getPollInterval().value(),
                Duration.ofSeconds(80)),
            //    private Boolean autoComplete;
            new Input<>(
                "autoComplete",
                CamundaClientJobWorkerProperties::setAutoComplete,
                j -> j.getAutoComplete().value(),
                true),
            //    private List<String> fetchVariables;
            new Input<>(
                "fetchVariables",
                CamundaClientJobWorkerProperties::setFetchVariables,
                j -> j.getFetchVariables().stream().map(SourceAware::value).toList(),
                List.of("var1", "var2", "var3")),
            //    private Boolean enabled;
            new Input<>(
                "enabled",
                CamundaClientJobWorkerProperties::setEnabled,
                j -> j.getEnabled().value(),
                true),
            //    private List<String> tenantIds;
            new Input<>(
                "tenantIds",
                CamundaClientJobWorkerProperties::setTenantIds,
                j -> j.getTenantIds().stream().map(SourceAware::value).toList(),
                List.of("tenant1", "tenant2", "tenant3")),
            //    private Boolean forceFetchAllVariables;
            new Input<>(
                "forceFetchAllVariables",
                CamundaClientJobWorkerProperties::setForceFetchAllVariables,
                j -> j.getForceFetchAllVariables().value(),
                false),
            //    private Boolean streamEnabled;
            new Input<>(
                "streamEnabled",
                CamundaClientJobWorkerProperties::setStreamEnabled,
                j -> j.getStreamEnabled().value(),
                true),
            //    private Duration streamTimeout;
            new Input<>(
                "streamTimeout",
                CamundaClientJobWorkerProperties::setStreamTimeout,
                j -> j.getStreamTimeout().value(),
                Duration.ofSeconds(30)),
            //    private Integer maxRetries;
            new Input<>(
                "maxRetries",
                CamundaClientJobWorkerProperties::setMaxRetries,
                j -> j.getMaxRetries().value(),
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
    customizer.customize(jobWorkerValue);
    final Object result = getter.apply(jobWorkerValue);
    if (result instanceof Collection<?>) {
      assertThat((Collection<?>) result).containsExactlyInAnyOrderElementsOf((Iterable) expected);
    } else {
      assertThat(result).isEqualTo(expected);
    }
  }

  @Test
  void shouldApplyWorkerDefaultTenantIds() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setTenantIds(List.of("customTenantId"));

    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);

    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds()).contains(new FromDefaultProperty<>("customTenantId"));
  }

  @Test
  void shouldMergeWorkerDefaultTenantIdsAndWorkerAnnotationTenantIds() {
    // given
    final CamundaClientProperties properties = properties();
    properties.getWorker().getDefaults().setTenantIds(List.of("customTenantId"));

    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);

    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setTenantIds(List.of(new FromAnnotation<>("annotationTenantId")));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds())
        .containsExactlyInAnyOrder(
            new FromAnnotation<>("annotationTenantId"),
            new FromDefaultProperty<>("customTenantId"));
  }

  @Test
  void shouldApplyClientDefaultTenantIdWhenNothingElseConfigured() {
    // given
    final CamundaClientProperties properties = properties();
    properties.setTenantId("testTenantId");
    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);
    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    customizer.customize(jobWorkerValue);
    assertThat(jobWorkerValue.getTenantIds())
        .containsOnly(new FromDefaultProperty<>("testTenantId"));
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
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds())
        .containsExactlyInAnyOrder(
            new FromDefaultProperty<>("testTenantId1"), new FromDefaultProperty<>("testTenantId2"));
  }

  @Test
  void shouldMergeClientDefaultTenantIdsAndWorkerAnnotationTenantIdsWhenNothingElseConfigured() {
    // given
    final CamundaClientProperties properties = properties();
    properties.setTenantId("customTenantId");

    final PropertyBasedJobWorkerValueCustomizer customizer =
        new PropertyBasedJobWorkerValueCustomizer(properties);

    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setTenantIds(List.of(new FromAnnotation<>("annotationTenantId")));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds())
        .containsExactlyInAnyOrder(
            new FromAnnotation<>("annotationTenantId"),
            new FromDefaultProperty<>("customTenantId"));
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
    jobWorkerValue.setTenantIds(List.of(new FromAnnotation<>("annotationWorkerDefaultsId")));
    jobWorkerValue.setType(new GeneratedFromMethodInfo<>("sampleWorker"));
    // when
    customizer.customize(jobWorkerValue);
    // then
    assertThat(jobWorkerValue.getTenantIds())
        .contains(new FromOverrideProperty<>("overriddenTenantId"));
  }

  @Test
  void shouldSetTenantFilterOnProperties() {
    // given
    final CamundaClientJobWorkerProperties properties = new CamundaClientJobWorkerProperties();

    // when
    properties.setTenantFilterMode(io.camunda.client.api.command.enums.TenantFilter.ASSIGNED);

    // then
    assertThat(properties.getTenantFilterMode())
        .isEqualTo(io.camunda.client.api.command.enums.TenantFilter.ASSIGNED);
  }

  @Test
  void shouldSetTenantFilterToProvided() {
    // given
    final CamundaClientJobWorkerProperties properties = new CamundaClientJobWorkerProperties();

    // when
    properties.setTenantFilterMode(io.camunda.client.api.command.enums.TenantFilter.PROVIDED);

    // then
    assertThat(properties.getTenantFilterMode())
        .isEqualTo(io.camunda.client.api.command.enums.TenantFilter.PROVIDED);
  }

  @Test
  void shouldInitializeWithDefaultTenantFilter() {
    // given/when
    final CamundaClientJobWorkerProperties properties = new CamundaClientJobWorkerProperties(true);

    // then
    assertThat(properties.getTenantFilterMode())
        .isEqualTo(io.camunda.client.api.command.enums.TenantFilter.PROVIDED);
  }

  private record Input<T>(
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
