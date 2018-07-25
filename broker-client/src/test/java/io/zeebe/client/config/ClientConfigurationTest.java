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
package io.zeebe.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.*;
import io.zeebe.client.impl.TopicClientImpl;
import io.zeebe.client.impl.ZeebeClientImpl;
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
    final ZeebeClient client = ZeebeClient.newClientBuilder().withProperties(props).build();
    closeables.manage(client);

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
    final ZeebeClient client = ZeebeClient.newClientBuilder().withProperties(config).build();
    closeables.manage(client);

    // then
    assertThat(client.getConfiguration().getDefaultJobWorkerName()).isEqualTo("me");
  }

  @Test
  public void shouldConfigureDefaultJobTimeout() {
    // given
    final Properties config = new Properties();
    config.setProperty(ClientProperties.DEFAULT_JOB_TIMEOUT, "5000");

    // when
    final ZeebeClient client = ZeebeClient.newClientBuilder().withProperties(config).build();
    closeables.manage(client);

    // then
    assertThat(client.getConfiguration().getDefaultJobTimeout()).isEqualTo(Duration.ofMillis(5000));
  }

  @Test
  public void shouldConfigureDefaultTopic() {
    // given
    final Properties config = new Properties();
    config.setProperty(ClientProperties.DEFAULT_TOPIC, "my-topic");

    // when
    final ZeebeClient client = ZeebeClient.newClientBuilder().withProperties(config).build();
    closeables.manage(client);

    // then
    assertThat(client.getConfiguration().getDefaultTopic()).isEqualTo("my-topic");

    final TopicClientImpl topicClient = (TopicClientImpl) client.topicClient();
    assertThat(topicClient.getTopic()).isEqualTo("my-topic");
  }

  @Test
  public void shouldConfigureDefaultBufferSizes() {
    // given
    final Properties config = new Properties();
    config.setProperty(ClientProperties.TOPIC_SUBSCRIPTION_BUFFER_SIZE, "123");
    config.setProperty(ClientProperties.JOB_SUBSCRIPTION_BUFFER_SIZE, "345");

    // when
    final ZeebeClient client = ZeebeClient.newClientBuilder().withProperties(config).build();
    closeables.manage(client);

    // then
    assertThat(client.getConfiguration().getDefaultTopicSubscriptionBufferSize()).isEqualTo(123);
    assertThat(client.getConfiguration().getDefaultJobSubscriptionBufferSize()).isEqualTo(345);
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
    assertThat(configuration.getDefaultTopic()).isEqualTo("default-topic");
    assertThat(configuration.getDefaultTopicSubscriptionBufferSize()).isEqualTo(1024);
    assertThat(configuration.getDefaultJobSubscriptionBufferSize()).isEqualTo(32);
  }
}
