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
package io.camunda.client.jobhandling.parameter;

import static io.camunda.client.spring.testsupport.BeanInfoUtil.parameterInfos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.Document;
import io.camunda.client.annotation.ProcessInstanceKey;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.bean.ParameterInfo;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.jobhandling.DocumentContext;
import io.camunda.client.jobhandling.parameter.ParameterResolverStrategy.ParameterResolverStrategyContext;
import io.camunda.zeebe.client.ZeebeClient;
import java.util.List;
import org.junit.jupiter.api.Test;

public class DefaultParameterResolverStrategyTest {

  @Test
  void shouldResolveLegacyParameter() {
    final ZeebeClient zeebeClient = mock(ZeebeClient.class);
    final CamundaClient camundaClient = mock(CamundaClient.class);
    final DefaultParameterResolverStrategy strategy =
        new DefaultParameterResolverStrategy(new CamundaObjectMapper(), zeebeClient);

    final List<ParameterInfo> parameters = parameterInfos(this, "legacyMethod");
    assertThat(parameters).hasSize(2);
    final ParameterResolver jobClientResolver =
        strategy.createResolver(
            new ParameterResolverStrategyContext(parameters.get(0), camundaClient));
    assertThat(jobClientResolver).isInstanceOf(CompatJobClientParameterResolver.class);
    final ParameterResolver activatedJobResolver =
        strategy.createResolver(
            new ParameterResolverStrategyContext(parameters.get(1), camundaClient));
    assertThat(activatedJobResolver).isInstanceOf(CompatActivatedJobParameterResolver.class);
    final Object resolvedJobClient = jobClientResolver.resolve(null, null);
    assertThat(resolvedJobClient).isEqualTo(zeebeClient);
  }

  @Test
  void shouldResolveCurrentParameter() {
    final CamundaClient camundaClient = mock(CamundaClient.class);

    final DefaultParameterResolverStrategy strategy =
        new DefaultParameterResolverStrategy(new CamundaObjectMapper());
    final List<ParameterInfo> parameters = parameterInfos(this, "currentMethod");
    assertThat(parameters).hasSize(2);
    final ParameterResolver jobClientResolver =
        strategy.createResolver(
            new ParameterResolverStrategyContext(parameters.get(0), camundaClient));
    assertThat(jobClientResolver).isInstanceOf(JobClientParameterResolver.class);
    final ParameterResolver activatedJobResolver =
        strategy.createResolver(
            new ParameterResolverStrategyContext(parameters.get(1), camundaClient));
    assertThat(activatedJobResolver).isInstanceOf(ActivatedJobParameterResolver.class);
  }

  @Test
  void shouldResolveDocument() {
    final CamundaClient camundaClient = mock(CamundaClient.class);
    final DefaultParameterResolverStrategy strategy =
        new DefaultParameterResolverStrategy(new CamundaObjectMapper());
    final List<ParameterInfo> parameters = parameterInfos(this, "documentMethod");
    assertThat(parameters).hasSize(1);
    final ParameterResolver parameterResolver =
        strategy.createResolver(
            new ParameterResolverStrategyContext(parameters.get(0), camundaClient));
    assertThat(parameterResolver).isInstanceOf(DocumentParameterResolver.class);
  }

  @Test
  void shouldResolveProcessInstanceKeyNative() {
    final CamundaClient camundaClient = mock(CamundaClient.class);
    final DefaultParameterResolverStrategy strategy =
        new DefaultParameterResolverStrategy(new CamundaObjectMapper());
    final List<ParameterInfo> parameters = parameterInfos(this, "processInstanceKeyNative");
    assertThat(parameters).hasSize(1);
    final ParameterResolver parameterResolver =
        strategy.createResolver(
            new ParameterResolverStrategyContext(parameters.get(0), camundaClient));
    assertThat(parameterResolver).isInstanceOf(KeyParameterResolver.class);
    final KeyParameterResolver processInstanceKeyParameterResolver =
        (KeyParameterResolver) parameterResolver;
    assertThat(processInstanceKeyParameterResolver.getKeyTargetType())
        .isEqualTo(KeyTargetType.LONG);
  }

  @Test
  void shouldResolveProcessInstanceKeyLong() {
    final CamundaClient camundaClient = mock(CamundaClient.class);
    final DefaultParameterResolverStrategy strategy =
        new DefaultParameterResolverStrategy(new CamundaObjectMapper());
    final List<ParameterInfo> parameters = parameterInfos(this, "processInstanceKeyLong");
    assertThat(parameters).hasSize(1);
    final ParameterResolver parameterResolver =
        strategy.createResolver(
            new ParameterResolverStrategyContext(parameters.get(0), camundaClient));
    assertThat(parameterResolver).isInstanceOf(KeyParameterResolver.class);
    final KeyParameterResolver processInstanceKeyParameterResolver =
        (KeyParameterResolver) parameterResolver;
    assertThat(processInstanceKeyParameterResolver.getKeyTargetType())
        .isEqualTo(KeyTargetType.LONG);
  }

  @Test
  void shouldResolveProcessInstanceKeyString() {
    final CamundaClient camundaClient = mock(CamundaClient.class);
    final DefaultParameterResolverStrategy strategy =
        new DefaultParameterResolverStrategy(new CamundaObjectMapper());
    final List<ParameterInfo> parameters = parameterInfos(this, "processInstanceKeyString");
    assertThat(parameters).hasSize(1);
    final ParameterResolver parameterResolver =
        strategy.createResolver(
            new ParameterResolverStrategyContext(parameters.get(0), camundaClient));
    assertThat(parameterResolver).isInstanceOf(KeyParameterResolver.class);
    final KeyParameterResolver processInstanceKeyParameterResolver =
        (KeyParameterResolver) parameterResolver;
    assertThat(processInstanceKeyParameterResolver.getKeyTargetType())
        .isEqualTo(KeyTargetType.STRING);
  }

  public void legacyMethod(
      final io.camunda.zeebe.client.api.worker.JobClient jobClient,
      final io.camunda.zeebe.client.api.response.ActivatedJob job) {}

  public void currentMethod(final JobClient jobClient, final ActivatedJob job) {}

  public void documentMethod(@Document final DocumentContext myDoc) {}

  public void processInstanceKeyNative(@ProcessInstanceKey final long processInstanceKey) {}

  public void processInstanceKeyLong(@ProcessInstanceKey final Long processInstanceKey) {}

  public void processInstanceKeyString(@ProcessInstanceKey final String processInstanceKey) {}
}
