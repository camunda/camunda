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

import java.time.Duration;
import java.util.Properties;

import org.junit.Rule;
import org.junit.Test;

import io.zeebe.client.ClientProperties;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.transport.ClientTransport;

public class ClientConfigurationTest
{

    protected static final long KEEP_ALIVE_TIMEOUT = 11234L;

    @Rule
    public StubBrokerRule broker = new StubBrokerRule();

    @Test
    public void shouldConfigureKeepAlive()
    {
        // given
        final Properties props = new Properties();
        props.put(ClientProperties.CLIENT_TCP_CHANNEL_KEEP_ALIVE_PERIOD, Long.toString(KEEP_ALIVE_TIMEOUT));

        final Duration expectedTimeout = Duration.ofMillis(KEEP_ALIVE_TIMEOUT);

        // when
        final ZeebeClient client = ZeebeClient.create(props);

        // then
        final ClientTransport transport = ((ZeebeClientImpl) client).getTransport();
        assertThat(transport.getChannelKeepAlivePeriod()).isEqualTo(expectedTimeout);
    }
}
