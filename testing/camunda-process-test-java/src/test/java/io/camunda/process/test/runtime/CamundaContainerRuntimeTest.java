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
package io.camunda.process.test.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.process.test.impl.runtime.CamundaContainerRuntime;
import org.junit.jupiter.api.Test;

public class CamundaContainerRuntimeTest {

  @Test
  void shouldCreateContainers() {
    // given/when
    final CamundaContainerRuntime runtime = CamundaContainerRuntime.newDefaultRuntime();

    // then
    assertThat(runtime.getZeebeContainer()).isNotNull();
    assertThat(runtime.getZeebeContainer().isRunning()).isFalse();

    assertThat(runtime.getElasticsearchContainer()).isNotNull();
    assertThat(runtime.getElasticsearchContainer().isRunning()).isFalse();

    assertThat(runtime.getOperateContainer()).isNotNull();
    assertThat(runtime.getOperateContainer().isRunning()).isFalse();

    assertThat(runtime.getTasklistContainer()).isNotNull();
    assertThat(runtime.getTasklistContainer().isRunning()).isFalse();
  }

  @Test
  void shouldStartAndStopContainers() throws Exception {
    // given
    final CamundaContainerRuntime runtime = CamundaContainerRuntime.newDefaultRuntime();

    // when
    runtime.start();

    // then
    assertThat(runtime.getZeebeContainer()).isNotNull();
    assertThat(runtime.getZeebeContainer().isRunning()).isTrue();

    assertThat(runtime.getElasticsearchContainer()).isNotNull();
    assertThat(runtime.getElasticsearchContainer().isRunning()).isTrue();

    assertThat(runtime.getOperateContainer()).isNotNull();
    assertThat(runtime.getOperateContainer().isRunning()).isTrue();

    assertThat(runtime.getTasklistContainer()).isNotNull();
    assertThat(runtime.getTasklistContainer().isRunning()).isTrue();

    // and when
    runtime.close();

    // then
    assertThat(runtime.getZeebeContainer()).isNotNull();
    assertThat(runtime.getZeebeContainer().isRunning()).isFalse();

    assertThat(runtime.getElasticsearchContainer()).isNotNull();
    assertThat(runtime.getElasticsearchContainer().isRunning()).isFalse();

    assertThat(runtime.getOperateContainer()).isNotNull();
    assertThat(runtime.getOperateContainer().isRunning()).isFalse();

    assertThat(runtime.getTasklistContainer()).isNotNull();
    assertThat(runtime.getTasklistContainer().isRunning()).isFalse();
  }
}
