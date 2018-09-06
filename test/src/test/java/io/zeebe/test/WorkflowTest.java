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
package io.zeebe.test;

import static io.zeebe.test.EmbeddedBrokerRule.DEFAULT_CONFIG_SUPPLIER;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.system.configuration.SocketBindingClientApiCfg;
import io.zeebe.gateway.ClientProperties;
import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.events.WorkflowInstanceEvent;
import io.zeebe.gateway.api.record.RecordType;
import io.zeebe.gateway.api.record.ValueType;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class WorkflowTest {
  @Rule
  public final ZeebeTestRule testRule =
      new ZeebeTestRule(
          DEFAULT_CONFIG_SUPPLIER,
          () -> {
            final Properties properties = new Properties();
            properties.setProperty(
                ClientProperties.BROKER_CONTACTPOINT,
                "localhost:" + (SocketBindingClientApiCfg.DEFAULT_PORT + 200));
            return properties;
          });

  private ZeebeClient client;

  @Before
  public void deploy() throws Exception {
    client = testRule.getClient();

    client
        .workflowClient()
        .newDeployCommand()
        .addResourceFromClasspath("process.bpmn")
        .send()
        .join();

    final CountDownLatch latch = new CountDownLatch(1);
    client
        .newSubscription()
        .name("deploy")
        .recordHandler(
            r -> {
              final ValueType valueType = r.getMetadata().getValueType();
              final RecordType recordType = r.getMetadata().getRecordType();
              final String intent = r.getMetadata().getIntent();
              if (recordType == RecordType.EVENT
                  && valueType == ValueType.DEPLOYMENT
                  && intent.equals("CREATED")) {
                latch.countDown();
              }
            })
        .open();

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void shouldCompleteWorkflowInstance() {
    final WorkflowInstanceEvent workflowInstance =
        client
            .workflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    client
        .jobClient()
        .newWorker()
        .jobType("task")
        .handler((c, j) -> c.newCompleteCommand(j).payload((String) null).send().join())
        .name("test")
        .open();

    testRule.waitUntilWorkflowInstanceCompleted(workflowInstance.getWorkflowInstanceKey());
  }
}
