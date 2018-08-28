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
package io.zeebe.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.commands.FinalCommandStep;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.impl.ZeebeClientImpl;
import io.zeebe.gateway.util.ClientRule;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class AsyncClusterRequestTest {

  private static final int PARTITION_ID = 123;

  public StubBrokerRule brokerRule = new StubBrokerRule();
  public ClientRule clientRule = new ClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  private TestRequestActor requestActor;

  @Before
  public void setUp() {
    brokerRule.addSystemTopic();
    brokerRule.addTopic(clientRule.getDefaultTopicName(), PARTITION_ID);

    requestActor = new TestRequestActor();
    ((ZeebeClientImpl) clientRule.getClient()).getScheduler().submitActor(requestActor);
  }

  @Test
  public void shouldSendRequestAsync() throws InterruptedException {
    // given
    brokerRule.jobs().registerCreateCommand();

    final FinalCommandStep<JobEvent> request =
        clientRule.getClient().topicClient().jobClient().newCreateCommand().jobType("foo");

    final CountDownLatch latch = new CountDownLatch(1);

    // when
    requestActor.awaitResponse(
        request,
        r -> {
          assertThat(r.getMetadata().getPartitionId()).isEqualTo(PARTITION_ID);
          latch.countDown();
        });

    // then
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
  }

  static class TestRequestActor extends Actor {

    @SuppressWarnings("unchecked")
    <R> void awaitResponse(FinalCommandStep<R> request, Consumer<R> responseConsumer) {
      actor.call(
          () ->
              actor.runOnCompletion(
                  (ActorFuture<R>) request.send(),
                  (r, t) -> {
                    if (t == null) {
                      responseConsumer.accept(r);
                    } else {
                      throw new AssertionError("Failed to send request", t);
                    }
                  }));
    }
  }
}
