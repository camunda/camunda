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
package io.camunda.spring.client.jobhandling.parameter;

import static io.camunda.spring.client.testsupport.ClassInfoUtil.parameterInfos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.spring.client.annotation.Document;
import io.camunda.spring.client.bean.ParameterInfo;
import io.camunda.spring.client.jobhandling.DocumentContext;
import io.camunda.zeebe.client.ZeebeClient;
import java.util.List;
import org.junit.jupiter.api.Test;

public class DefaultParameterResolverStrategyTest {

  @Test
  void shouldResolveLegacyParameter() {
    final ZeebeClient zeebeClient = mock(ZeebeClient.class);
    final DefaultParameterResolverStrategy strategy =
        new DefaultParameterResolverStrategy(new CamundaObjectMapper(), zeebeClient);

    final List<ParameterInfo> parameters = parameterInfos(this, "legacyMethod");
    assertThat(parameters).hasSize(2);
    final ParameterResolver jobClientResolver = strategy.createResolver(parameters.get(0));
    assertThat(jobClientResolver).isInstanceOf(CompatJobClientParameterResolver.class);
    final ParameterResolver activatedJobResolver = strategy.createResolver(parameters.get(1));
    assertThat(activatedJobResolver).isInstanceOf(CompatActivatedJobParameterResolver.class);
    final Object resolvedJobClient = jobClientResolver.resolve(null, null);
    assertThat(resolvedJobClient).isEqualTo(zeebeClient);
  }

  @Test
  void shouldResolveCurrentParameter() {
    final DefaultParameterResolverStrategy strategy =
        new DefaultParameterResolverStrategy(new CamundaObjectMapper());
    final List<ParameterInfo> parameters = parameterInfos(this, "currentMethod");
    assertThat(parameters).hasSize(2);
    final ParameterResolver jobClientResolver = strategy.createResolver(parameters.get(0));
    assertThat(jobClientResolver).isInstanceOf(JobClientParameterResolver.class);
    final ParameterResolver activatedJobResolver = strategy.createResolver(parameters.get(1));
    assertThat(activatedJobResolver).isInstanceOf(ActivatedJobParameterResolver.class);
  }

  @Test
  void shouldResolveDocument() {
    final DefaultParameterResolverStrategy strategy =
        new DefaultParameterResolverStrategy(new CamundaObjectMapper());
    final List<ParameterInfo> parameters = parameterInfos(this, "documentMethod");
    assertThat(parameters).hasSize(1);
    final ParameterResolver parameterResolver = strategy.createResolver(parameters.get(0));
    assertThat(parameterResolver).isInstanceOf(DocumentResolver.class);
  }

  public void legacyMethod(
      final io.camunda.zeebe.client.api.worker.JobClient jobClient,
      final io.camunda.zeebe.client.api.response.ActivatedJob job) {}

  public void currentMethod(final JobClient jobClient, final ActivatedJob job) {}

  public void documentMethod(@Document final DocumentContext myDoc) {}
}
