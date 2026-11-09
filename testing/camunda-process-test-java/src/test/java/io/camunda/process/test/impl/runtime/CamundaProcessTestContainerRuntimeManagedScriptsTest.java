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
package io.camunda.process.test.impl.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.process.test.impl.containers.ConnectorsContainer;
import io.camunda.process.test.impl.containers.ContainerFactory;
import io.camunda.process.test.impl.containers.ManagedScriptsImage;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CamundaProcessTestContainerRuntimeManagedScriptsTest {

  // The first Connectors version with managed scripts.
  private static final String CONNECTORS_VERSION = "8.11.0";

  @Mock private ContainerFactory containerFactory;

  @Mock(answer = Answers.RETURNS_SELF)
  private CamundaContainer camundaContainer;

  @Mock(answer = Answers.RETURNS_SELF)
  private ConnectorsContainer connectorsContainer;

  @BeforeEach
  void configureMocks() {
    when(containerFactory.createCamundaContainer(any(), any())).thenReturn(camundaContainer);
    // Lenient: not called when the configuration is rejected.
    lenient()
        .when(containerFactory.createManagedScriptsConnectorsContainer(any(), anyBoolean()))
        .thenReturn(connectorsContainer);
  }

  @Test
  void shouldBuildTheConnectorsImageWithTheInterpreters() {
    // when
    CamundaProcessTestContainerRuntime.newBuilder()
        .withContainerFactory(containerFactory)
        .withManagedScriptsEnabled(true)
        .withConnectorsDockerImageName("custom/connectors")
        .withConnectorsDockerImageVersion("8.11.0")
        .withManagedScriptsPythonVersion("3.12")
        .withManagedScriptsNodeVersion("22")
        .build();

    // then
    final ArgumentCaptor<ManagedScriptsImage> image =
        ArgumentCaptor.forClass(ManagedScriptsImage.class);
    verify(containerFactory).createManagedScriptsConnectorsContainer(image.capture(), eq(false));
    verify(containerFactory, never()).createConnectorsContainer(any(), any());
    assertThat(image.getValue().dockerfile("1001:1001"))
        .startsWith("FROM custom/connectors:8.11.0\n")
        .contains("apk add --no-cache python-3.12 nodejs-22");
    // The built image is not pulled.
    verify(connectorsContainer, never()).withImagePullPolicy(any());
  }

  @Test
  void shouldPullAMutableBaseImageWhenBuilding() {
    // when
    CamundaProcessTestContainerRuntime.newBuilder()
        .withContainerFactory(containerFactory)
        .withManagedScriptsEnabled(true)
        .withConnectorsDockerImageVersion("8.11-SNAPSHOT")
        .build();

    // then
    verify(containerFactory).createManagedScriptsConnectorsContainer(any(), eq(true));
  }

  @Test
  void shouldEnableTheLocalProviderBeforeTheUserEnvironment() {
    // when
    CamundaProcessTestContainerRuntime.newBuilder()
        .withContainerFactory(containerFactory)
        .withManagedScriptsEnabled(true)
        .withConnectorsDockerImageVersion(CONNECTORS_VERSION)
        .withConnectorsEnv(ManagedScriptsImage.ENV_MANAGED_CODE_LOCAL_DIRECTORY, "/tmp/custom")
        .build();

    // then
    final InOrder inOrder = inOrder(connectorsContainer);
    inOrder.verify(connectorsContainer).withEnv(ManagedScriptsImage.envVars());
    inOrder
        .verify(connectorsContainer)
        .withEnv(
            Collections.singletonMap(
                ManagedScriptsImage.ENV_MANAGED_CODE_LOCAL_DIRECTORY, "/tmp/custom"));
  }

  @Test
  void shouldStartTheConnectorsWithManagedScripts() throws Exception {
    // given
    final CamundaProcessTestRuntime runtime =
        CamundaProcessTestContainerRuntime.newBuilder()
            .withContainerFactory(containerFactory)
            .withConnectorsEnabled(false)
            .withManagedScriptsEnabled(true)
            .withConnectorsDockerImageVersion(CONNECTORS_VERSION)
            .build();

    // when
    runtime.start();

    // then
    verify(connectorsContainer).start();
    runtime.close();
  }

  @Test
  void shouldRejectAnUnsupportedVersion() {
    // given
    final CamundaProcessTestRuntimeBuilder builder =
        CamundaProcessTestContainerRuntime.newBuilder()
            .withContainerFactory(containerFactory)
            .withManagedScriptsEnabled(true)
            .withConnectorsDockerImageVersion(CONNECTORS_VERSION)
            .withManagedScriptsNodeVersion("18");

    // when/then
    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid Node.js version '18'");
  }

  @Test
  void shouldRejectAConnectorsVersionWithoutManagedScripts() {
    // given
    final CamundaProcessTestRuntimeBuilder builder =
        CamundaProcessTestContainerRuntime.newBuilder()
            .withContainerFactory(containerFactory)
            .withManagedScriptsEnabled(true)
            .withConnectorsDockerImageVersion("8.10.2");

    // when/then
    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Managed scripts need Connectors 8.11 or later");
    verify(containerFactory, never()).createManagedScriptsConnectorsContainer(any(), anyBoolean());
  }
}
