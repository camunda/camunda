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

import static io.camunda.zeebe.client.impl.util.DataSizeUtil.ONE_KB;
import static io.camunda.zeebe.client.impl.util.DataSizeUtil.ONE_MB;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.impl.NoopCredentialsProvider;
import io.camunda.zeebe.spring.client.config.legacy.ZeebeClientStarterAutoConfigurationTest;
import io.camunda.zeebe.spring.client.configuration.CamundaAutoConfiguration;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
    classes = {
      CamundaAutoConfiguration.class,
      ZeebeClientStarterAutoConfigurationTest.TestConfig.class
    })
public class ZeebeClientConfigurationDefaultPropertiesTest {

  @Autowired private ApplicationContext applicationContext;

  @Test
  void testDefaultClientConfiguration() throws URISyntaxException {
    final ZeebeClient client = applicationContext.getBean(ZeebeClient.class);

    assertThat(client.getConfiguration().isPlaintextConnectionEnabled()).isTrue();
    assertThat(client.getConfiguration().getCaCertificatePath()).isNull();
    assertThat(client.getConfiguration().getCredentialsProvider())
        .isInstanceOf(NoopCredentialsProvider.class);
    assertThat(client.getConfiguration().getDefaultJobPollInterval())
        .isEqualTo(Duration.ofMillis(100));
    assertThat(client.getConfiguration().getDefaultJobTimeout()).isEqualTo(Duration.ofMinutes(5));
    assertThat(client.getConfiguration().getDefaultJobWorkerMaxJobsActive()).isEqualTo(32);
    assertThat(client.getConfiguration().getDefaultJobWorkerName()).isEqualTo("default");
    assertThat(client.getConfiguration().getDefaultJobWorkerStreamEnabled()).isFalse();
    assertThat(client.getConfiguration().getDefaultJobWorkerTenantIds())
        .isEqualTo(Collections.singletonList("<default>"));
    assertThat(client.getConfiguration().getDefaultMessageTimeToLive())
        .isEqualTo(Duration.ofHours(1));
    assertThat(client.getConfiguration().getDefaultRequestTimeout())
        .isEqualTo(Duration.ofSeconds(10));
    assertThat(client.getConfiguration().getDefaultTenantId()).isEqualTo("<default>");
    assertThat(client.getConfiguration().getGatewayAddress()).isEqualTo("0.0.0.0:26500");
    assertThat(client.getConfiguration().getGrpcAddress())
        .isEqualTo(new URI("http://0.0.0.0:26500"));
    assertThat(client.getConfiguration().getKeepAlive()).isEqualTo(Duration.ofSeconds(45));
    assertThat(client.getConfiguration().getMaxMessageSize()).isEqualTo(5 * ONE_MB);
    assertThat(client.getConfiguration().getMaxMetadataSize()).isEqualTo(16 * ONE_KB);
    assertThat(client.getConfiguration().getNumJobWorkerExecutionThreads()).isEqualTo(1);
    assertThat(client.getConfiguration().getOverrideAuthority()).isNull();
    assertThat(client.getConfiguration().getRestAddress())
        .isEqualTo(new URI("http://0.0.0.0:8080"));
    assertThat(client.getConfiguration().preferRestOverGrpc()).isFalse();
  }
}
