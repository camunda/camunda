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
package io.zeebe.broker.it.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.grpc.StatusRuntimeException;
import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.cmd.ClientException;
import java.net.ConnectException;
import java.util.concurrent.ExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class ExceptionHandlingTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public GrpcClientRule clientRule =
      new GrpcClientRule(
          brokerRule,
          zeebeClientBuilder -> zeebeClientBuilder.brokerContactPoint("localhost:1234"));

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void shouldContainRootCauses() {
    final Throwable throwable = catchThrowable(() -> clientRule.getPartitions());

    assertThat(throwable).isInstanceOf(ClientException.class).hasMessageContaining("io exception");

    final Throwable firstCause = throwable.getCause();
    assertThat(firstCause)
        .isInstanceOf(ExecutionException.class)
        .hasMessageContaining("io.grpc.StatusRuntimeException: UNAVAILABLE: io exception");

    final Throwable secondCause = firstCause.getCause();
    assertThat(secondCause)
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessageContaining("UNAVAILABLE: io exception");

    final Throwable thirdCause = secondCause.getCause();
    assertThat(thirdCause)
        .hasCauseInstanceOf(ConnectException.class)
        .hasMessageContaining("Connection refused:")
        .hasMessageContaining("localhost")
        .hasMessageContaining(":1234");
  }
}
