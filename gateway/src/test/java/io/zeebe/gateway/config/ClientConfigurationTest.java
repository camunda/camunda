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
package io.zeebe.gateway.config;

import static io.zeebe.protocol.Protocol.DEFAULT_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.ClientProperties;
import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.ZeebeClientConfiguration;
import io.zeebe.gateway.impl.ZeebeClientImpl;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.ClientTransport;
import java.time.Duration;
import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;

public class ClientConfigurationTest {
  private static final long KEEP_ALIVE_TIMEOUT = 11234L;

  @Rule public StubBrokerRule broker = new StubBrokerRule();

  @Rule public AutoCloseableRule closeables = new AutoCloseableRule();

  @Test
  public void shouldConfigureKeepAlive() {
    // given
    final Properties props = new Properties();
    props.put(ClientProperties.TCP_CHANNEL_KEEP_ALIVE_PERIOD, Long.toString(KEEP_ALIVE_TIMEOUT));

    final Duration expectedTimeout = Duration.ofMillis(KEEP_ALIVE_TIMEOUT);

    // when
    final ZeebeClient client = buildClient(props);

    // then
    final ClientTransport transport = ((ZeebeClientImpl) client).getTransport();
    assertThat(transport.getChannelKeepAlivePeriod()).isEqualTo(expectedTimeout);
  }

  @Test
  public void shouldConfigureDefaultJobWorker() {
    // given
    final Properties config = new Properties();
    config.setProperty(ClientProperties.DEFAULT_JOB_WORKER_NAME, "me");

    // when
    final ZeebeClient client = buildClient(config);

    // then
    assertThat(client.getConfiguration().getDefaultJobWorkerName()).isEqualTo("me");
  }

  @Test
  public void shouldConfigureDefaultJobTimeout() {
    // given
    final Properties config = new Properties();
    config.setProperty(ClientProperties.DEFAULT_JOB_TIMEOUT, "5000");

    // when
    final ZeebeClient client = buildClient(config);

    // then
    assertThat(client.getConfiguration().getDefaultJobTimeout()).isEqualTo(Duration.ofMillis(5000));
  }

  @Test
  public void shouldConfigureDefaultBufferSizes() {
    // given
    final Properties config = new Properties();
    config.setProperty(ClientProperties.TOPIC_SUBSCRIPTION_BUFFER_SIZE, "123");
    config.setProperty(ClientProperties.JOB_SUBSCRIPTION_BUFFER_SIZE, "345");

    // when
    final ZeebeClient client = buildClient(config);

    // then
    assertThat(client.getConfiguration().getDefaultTopicSubscriptionBufferSize()).isEqualTo(123);
    assertThat(client.getConfiguration().getDefaultJobSubscriptionBufferSize()).isEqualTo(345);
  }

  @Test
  public void shouldConfigureDefaultMessageTimeToLive() {
    // given
    final Properties config = new Properties();
    config.setProperty(ClientProperties.DEFAULT_MESSAGE_TIME_TO_LIVE, "123");

    // when
    final ZeebeClient client = buildClient(config);

    // then
    assertThat(client.getConfiguration().getDefaultMessageTimeToLive())
        .isEqualTo(Duration.ofMillis(123));
  }

  @Test
  public void shouldApplyDefaults() {
    // given
    final ZeebeClient client = ZeebeClient.newClient();
    closeables.manage(client);

    // then
    final ZeebeClientConfiguration configuration = client.getConfiguration();

    assertThat(configuration.getDefaultJobWorkerName()).isEqualTo("default");
    assertThat(configuration.getDefaultJobTimeout()).isEqualTo(Duration.ofMinutes(5));
    assertThat(configuration.getDefaultTopic()).isEqualTo(DEFAULT_TOPIC);
    assertThat(configuration.getDefaultTopicSubscriptionBufferSize()).isEqualTo(1024);
    assertThat(configuration.getDefaultJobSubscriptionBufferSize()).isEqualTo(32);
    assertThat(configuration.getDefaultMessageTimeToLive()).isEqualTo(Duration.ofHours(1));
  }

  private ZeebeClient buildClient(Properties config) {
    final ZeebeClient client =
        ZeebeClient.newClientBuilder()
            .withProperties(config)
            .brokerContactPoint(broker.getSocketAddress().toString())
            .build();

    closeables.manage(client);

    return client;
  }
}
