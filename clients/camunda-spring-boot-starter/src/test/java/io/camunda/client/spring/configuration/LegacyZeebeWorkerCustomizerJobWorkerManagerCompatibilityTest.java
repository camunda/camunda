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
package io.camunda.client.spring.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.annotation.value.SourceAware;
import io.camunda.client.jobhandling.JobWorkerFactory;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.jobhandling.ManagedJobWorker;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.zeebe.spring.client.annotation.customizer.ZeebeWorkerValueCustomizer;
import io.camunda.zeebe.spring.client.annotation.value.ZeebeWorkerValue;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression test for https://github.com/camunda/camunda/issues/49131 on stable/8.9.
 *
 * <p>On stable/8.9, customizing was moved from {@code JobWorkerAnnotationProcessor} into {@code
 * JobWorkerManager} (commit b2b7f35648b). The fix ensures that legacy {@link
 * ZeebeWorkerValueCustomizer} beans are wrapped as {@link
 * io.camunda.client.spring.annotation.customizer.JobWorkerValueCustomizerCompat} and injected into
 * {@code JobWorkerManager} by {@code CamundaClientAllAutoConfiguration.jobWorkerManager()}, rather
 * than relying on {@code LegacyJobWorkerValueCustomizerBeanDefinitionRegistryPostProcessor} to
 * register compat beans dynamically — a pattern that is incompatible with Spring AOT.
 */
@ExtendWith(MockitoExtension.class)
class LegacyZeebeWorkerCustomizerJobWorkerManagerCompatibilityTest {

  @Mock JobWorkerFactory jobWorkerFactory;
  @Mock ManagedJobWorker managedJobWorker;

  @Test
  void shouldApplyLegacyZeebeWorkerValueCustomizerViaJobWorkerManager() {
    // given — production config wired the same way it is after the fix
    final TrackingZeebeCustomizer legacyCustomizer = new TrackingZeebeCustomizer();
    final CamundaClientAllAutoConfiguration config =
        new CamundaClientAllAutoConfiguration(mock(CamundaClientProperties.class));

    final JobWorkerManager jobWorkerManager =
        config.jobWorkerManager(List.of(), List.of(legacyCustomizer), jobWorkerFactory);

    final JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setType(new SourceAware.FromAnnotation<>("test-type"));
    jobWorkerValue.setEnabled(new SourceAware.FromAnnotation<>(false));
    when(managedJobWorker.jobWorkerValue()).thenReturn(jobWorkerValue);

    // when
    jobWorkerManager.createJobWorker(mock(CamundaClient.class), managedJobWorker, this);

    // then — the legacy customizer was invoked via the compat wrapper
    assertThat(legacyCustomizer.wasCalled()).isTrue();
  }

  static class TrackingZeebeCustomizer implements ZeebeWorkerValueCustomizer {
    private boolean called = false;

    @Override
    public void customize(final ZeebeWorkerValue worker) {
      called = true;
    }

    public boolean wasCalled() {
      return called;
    }
  }
}
