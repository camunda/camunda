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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestRuntimeMode;
import io.camunda.process.test.impl.runtime.CamundaProcessTestSharedRuntime.SharedRuntimeStorage;
import java.net.URI;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CamundaProcessTestSharedRuntimeTest {

  private static final URI GRPC_API_ADDRESS = URI.create("http://my-host:100");
  private static final URI REST_API_ADDRESS = URI.create("http://my-host:101");
  private static final URI MONITORING_API_ADDRESS = URI.create("http://my-host:103");
  private static final URI CONNECTORS_REST_API_ADDRESS = URI.create("http://my-host:104");

  @Mock private Supplier<CamundaProcessTestContainerRuntime> runtimeBuilder;
  @Mock private CamundaProcessTestContainerRuntime containerRuntime;
  @Mock private SharedRuntimeStorage sharedRuntimeStorage;
  @Mock private CamundaClientBuilderFactory camundaClientBuilderFactory;

  @Test
  void shouldCreateRuntimeFromBuilder() {
    // given/when
    final CamundaProcessTestRuntime runtime =
        CamundaProcessTestContainerRuntime.newBuilder()
            .withRuntimeMode(CamundaProcessTestRuntimeMode.SHARED)
            .build();

    // then
    assertThat(runtime).isInstanceOf(CamundaProcessTestSharedRuntime.class);
  }

  @Test
  void shouldCreateContainerRuntime() {
    // given
    when(runtimeBuilder.get()).thenReturn(containerRuntime);

    // when
    new CamundaProcessTestSharedRuntime(runtimeBuilder, sharedRuntimeStorage);

    // then
    verify(runtimeBuilder).get();
    verify(sharedRuntimeStorage).setRuntime(containerRuntime);
  }

  @Test
  void shouldCreateContainerRuntimeOnlyOnce() {
    // given
    when(sharedRuntimeStorage.getRuntime()).thenReturn(containerRuntime);

    // when
    new CamundaProcessTestSharedRuntime(runtimeBuilder, sharedRuntimeStorage);

    // then
    verify(sharedRuntimeStorage).getRuntime();
    verify(sharedRuntimeStorage, never()).setRuntime(any());
    verify(runtimeBuilder, never()).get();
  }

  @Test
  void shouldStartRuntime() {
    // given
    when(sharedRuntimeStorage.getRuntime()).thenReturn(containerRuntime);
    when(containerRuntime.isStarted()).thenReturn(false);

    // when
    final CamundaProcessTestSharedRuntime sharedRuntime =
        new CamundaProcessTestSharedRuntime(runtimeBuilder, sharedRuntimeStorage);
    sharedRuntime.start();

    // then
    verify(containerRuntime).start();
  }

  @Test
  void shouldStartRuntimeOnlyOnce() {
    // given
    when(sharedRuntimeStorage.getRuntime()).thenReturn(containerRuntime);
    when(containerRuntime.isStarted()).thenReturn(true);

    // when
    final CamundaProcessTestSharedRuntime sharedRuntime =
        new CamundaProcessTestSharedRuntime(runtimeBuilder, sharedRuntimeStorage);
    sharedRuntime.start();

    // then
    verify(containerRuntime, never()).start();
  }

  @Test
  void shouldNotCloseRuntime() throws Exception {
    // given
    when(sharedRuntimeStorage.getRuntime()).thenReturn(containerRuntime);

    // when
    final CamundaProcessTestSharedRuntime sharedRuntime =
        new CamundaProcessTestSharedRuntime(runtimeBuilder, sharedRuntimeStorage);
    sharedRuntime.start();
    sharedRuntime.close();

    // then
    verify(containerRuntime, never()).close();
  }

  @Test
  void shouldRetrieveAddressesFromRuntime() {
    // given
    when(sharedRuntimeStorage.getRuntime()).thenReturn(containerRuntime);
    when(containerRuntime.getCamundaGrpcApiAddress()).thenReturn(GRPC_API_ADDRESS);
    when(containerRuntime.getCamundaRestApiAddress()).thenReturn(REST_API_ADDRESS);
    when(containerRuntime.getCamundaMonitoringApiAddress()).thenReturn(MONITORING_API_ADDRESS);
    when(containerRuntime.getConnectorsRestApiAddress()).thenReturn(CONNECTORS_REST_API_ADDRESS);

    // when
    final CamundaProcessTestSharedRuntime sharedRuntime =
        new CamundaProcessTestSharedRuntime(runtimeBuilder, sharedRuntimeStorage);
    sharedRuntime.start();

    // then
    assertThat(sharedRuntime.getCamundaGrpcApiAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(sharedRuntime.getCamundaRestApiAddress()).isEqualTo(REST_API_ADDRESS);
    assertThat(sharedRuntime.getCamundaMonitoringApiAddress()).isEqualTo(MONITORING_API_ADDRESS);
    assertThat(sharedRuntime.getConnectorsRestApiAddress()).isEqualTo(CONNECTORS_REST_API_ADDRESS);
  }

  @Test
  void shouldRetrieveClientBuilderFromRuntime() {
    // given
    when(sharedRuntimeStorage.getRuntime()).thenReturn(containerRuntime);
    when(containerRuntime.getCamundaClientBuilderFactory()).thenReturn(camundaClientBuilderFactory);

    // when
    final CamundaProcessTestSharedRuntime sharedRuntime =
        new CamundaProcessTestSharedRuntime(runtimeBuilder, sharedRuntimeStorage);
    sharedRuntime.start();

    // then
    assertThat(sharedRuntime.getCamundaClientBuilderFactory())
        .isEqualTo(camundaClientBuilderFactory);
  }
}
