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

import static org.assertj.core.api.Assertions.fail;

import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.gateway.ClientProperties;
import io.zeebe.gateway.ZeebeClient;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.stream.StreamWrapperException;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.rules.ExternalResource;

public class ZeebeTestRule extends ExternalResource {
  private final EmbeddedBrokerRule brokerRule;
  private final ClientRule clientRule;

  public ZeebeTestRule() {
    this(EmbeddedBrokerRule.DEFAULT_CONFIG_FILE, Properties::new);
  }

  public ZeebeTestRule(
      final String configFileClasspathLocation, final Supplier<Properties> propertiesProvider) {
    brokerRule = new EmbeddedBrokerRule(configFileClasspathLocation);
    clientRule =
        new ClientRule(
            () -> {
              final Properties properties = propertiesProvider.get();
              properties.setProperty(
                  ClientProperties.BROKER_CONTACTPOINT, brokerRule.getClientAddress().toString());
              return properties;
            });
  }

  public ZeebeClient getClient() {
    return clientRule.getClient();
  }

  public BrokerCfg getBrokerCfg() {
    return brokerRule.getBrokerCfg();
  }

  @Override
  protected void before() {
    brokerRule.before();
    clientRule.before();
  }

  @Override
  protected void after() {
    clientRule.after();
    brokerRule.after();
  }

  public void waitUntilWorkflowInstanceCompleted(final long key) {
    try {
      RecordingExporter.workflowInstanceRecords().withWorkflowInstanceKey(key).getFirst();
    } catch (StreamWrapperException swe) {
      throw new RuntimeException(
          String.format("Expected to find workflow instance with key %s.", key), swe);
    }

    try {
      RecordingExporter.workflowInstanceRecords()
          .withWorkflowInstanceKey(key)
          .withKey(key)
          .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
          .getLast();
    } catch (StreamWrapperException swe) {
      throw new RuntimeException(
          String.format(
              "Expected to find workflow instance with key %s in state ELEMENT_COMPLETED.", key),
          swe);
    }
  }

  public void waitUntilJobCompleted(final long key) {
    try {
      RecordingExporter.jobRecords().withKey(key).getFirst();
    } catch (StreamWrapperException swe) {
      throw new RuntimeException(String.format("Expected to find job with key %s.", key), swe);
    }

    try {
      RecordingExporter.jobRecords(JobIntent.COMPLETED).withKey(key).getLast();
    } catch (StreamWrapperException swe) {
      throw new RuntimeException(
          String.format("Expected to find job with key %s in state COMPLETED.", key), swe);
    }
  }

  public void printWorkflowInstanceEvents(final long key) {
    RecordingExporter.workflowInstanceRecords()
        .withWorkflowInstanceKey(key)
        .forEach(
            event -> {
              System.out.println("> " + event.toJson());
            });
  }

  private void waitUntil(final BooleanSupplier condition, final String failureMessage) {
    final long timeout = Instant.now().plus(Duration.ofSeconds(5)).toEpochMilli();

    while (!condition.getAsBoolean() && System.currentTimeMillis() < timeout) {
      sleep(100);
    }

    if (!condition.getAsBoolean()) {
      fail(failureMessage);
    }
  }

  private void sleep(final int millis) {
    try {
      Thread.sleep(millis);
    } catch (final InterruptedException e) {
      // ignore
    }
  }

  public int getDefaultPartition() {
    return clientRule.getDefaultPartition();
  }
}
