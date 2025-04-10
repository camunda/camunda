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
package io.camunda.zeebe.spring.client.config;

import static io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientAllAutoConfiguration;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientProdAutoConfiguration;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureExtension;

@SpringBootTest(
    classes = {ZeebeClientAllAutoConfiguration.class, ZeebeClientProdAutoConfiguration.class},
    properties = {
      "camunda.client.mode=self-managed",
      "camunda.client.auth.client-id=my-client-id",
      "camunda.client.auth.client-secret=my-client-secret"
    })
@ExtendWith(OutputCaptureExtension.class)
public class ZeebeClientConfigurationImplSelfManagedTest {
  @Autowired ZeebeClientConfiguration zeebeClientConfiguration;
  @Autowired JsonMapper jsonMapper;
  @Autowired ZeebeClientExecutorService zeebeClientExecutorService;

  @Test
  void shouldContainsZeebeClientConfiguration() {
    assertThat(zeebeClientConfiguration).isNotNull();
  }

  @Test
  void shouldNotHaveCredentialsProvider() {
    assertThat(zeebeClientConfiguration.getCredentialsProvider())
        .isInstanceOf(OAuthCredentialsProvider.class);
  }

  @Test
  void shouldHaveGrpcAddress() {
    assertThat(zeebeClientConfiguration.getGrpcAddress().toString())
        .isEqualTo("http://localhost:26500");
  }

  @Test
  void shouldHaveRestAddress() {
    assertThat(zeebeClientConfiguration.getRestAddress().toString())
        .isEqualTo("http://localhost:8088");
  }

  @Test
  void shouldHaveDefaultTenantId() {
    assertThat(zeebeClientConfiguration.getDefaultTenantId())
        .isEqualTo(DEFAULT.getDefaultTenantId());
  }

  @Test
  void shouldHaveDefaultJobWorkerTenantIds() {
    assertThat(zeebeClientConfiguration.getDefaultJobWorkerTenantIds())
        .isEqualTo(DEFAULT.getDefaultJobWorkerTenantIds());
  }

  @Test
  void shouldHaveNumJobWorkerExecutionThreads() {
    assertThat(zeebeClientConfiguration.getNumJobWorkerExecutionThreads())
        .isEqualTo(DEFAULT.getNumJobWorkerExecutionThreads());
  }

  @Test
  void shouldHaveDefaultJobWorkerMaxJobsActive() {
    assertThat(zeebeClientConfiguration.getDefaultJobWorkerMaxJobsActive())
        .isEqualTo(DEFAULT.getDefaultJobWorkerMaxJobsActive());
  }

  @Test
  void shouldHaveDefaultJobWorkerName() {
    assertThat(zeebeClientConfiguration.getDefaultJobWorkerName())
        .isEqualTo(DEFAULT.getDefaultJobWorkerName());
  }

  @Test
  void shouldHaveDefaultJobTimeout() {
    assertThat(zeebeClientConfiguration.getDefaultJobTimeout())
        .isEqualTo(DEFAULT.getDefaultJobTimeout());
  }

  @Test
  void shouldHaveDefaultJobPollInterval() {
    assertThat(zeebeClientConfiguration.getDefaultJobPollInterval())
        .isEqualTo(DEFAULT.getDefaultJobPollInterval());
  }

  @Test
  void shouldHaveDefaultMessageTimeToLive() {
    assertThat(zeebeClientConfiguration.getDefaultMessageTimeToLive())
        .isEqualTo(DEFAULT.getDefaultMessageTimeToLive());
  }

  @Test
  void shouldHaveDefaultRequestTimeout() {
    assertThat(zeebeClientConfiguration.getDefaultRequestTimeout())
        .isEqualTo(DEFAULT.getDefaultRequestTimeout());
  }

  @Test
  void shouldHavePlaintextConnectionEnabled() {
    assertThat(zeebeClientConfiguration.isPlaintextConnectionEnabled()).isEqualTo(true);
  }

  @Test
  void shouldHaveCaCertificatePath() {
    assertThat(zeebeClientConfiguration.getCaCertificatePath())
        .isEqualTo(DEFAULT.getCaCertificatePath());
  }

  @Test
  void shouldHaveKeepAlive() {
    assertThat(zeebeClientConfiguration.getKeepAlive()).isEqualTo(DEFAULT.getKeepAlive());
  }

  @Test
  void shouldNotHaveClientInterceptors() {
    assertThat(zeebeClientConfiguration.getInterceptors()).isEmpty();
  }

  @Test
  void shouldNotHaveAsyncClientChainHandlers() {
    assertThat(zeebeClientConfiguration.getChainHandlers()).isEmpty();
  }

  @Test
  void shouldHaveJsonMapper() {
    assertThat(zeebeClientConfiguration.getJsonMapper()).isEqualTo(jsonMapper);
  }

  @Test
  void shouldHaveOverrideAuthority() {
    assertThat(zeebeClientConfiguration.getOverrideAuthority())
        .isEqualTo(DEFAULT.getOverrideAuthority());
  }

  @Test
  void shouldHaveMaxMessageSize() {
    assertThat(zeebeClientConfiguration.getMaxMessageSize()).isEqualTo(DEFAULT.getMaxMessageSize());
  }

  @Test
  void shouldHaveMaxMetadataSize() {
    assertThat(zeebeClientConfiguration.getMaxMetadataSize())
        .isEqualTo(DEFAULT.getMaxMetadataSize());
  }

  @Test
  void shouldHaveJobWorkerExecutor() {
    assertThat(zeebeClientConfiguration.jobWorkerExecutor())
        .isEqualTo(zeebeClientExecutorService.get());
  }

  @Test
  void shouldHaveOwnsJobWorkerExecutor() {
    assertThat(zeebeClientConfiguration.ownsJobWorkerExecutor()).isEqualTo(true);
  }

  @Test
  void shouldHaveDefaultJobWorkerStreamEnabled() {
    assertThat(zeebeClientConfiguration.getDefaultJobWorkerStreamEnabled())
        .isEqualTo(DEFAULT.getDefaultJobWorkerStreamEnabled());
  }

  @Test
  void shouldHaveDefaultRetryPolicy() {
    assertThat(zeebeClientConfiguration.useDefaultRetryPolicy())
        .isEqualTo(DEFAULT.useDefaultRetryPolicy());
  }

  @Test
  void shouldHaveDefaultPreferRestOverGrpc() {
    assertThat(zeebeClientConfiguration.preferRestOverGrpc())
        .isEqualTo(DEFAULT.preferRestOverGrpc());
  }
}
