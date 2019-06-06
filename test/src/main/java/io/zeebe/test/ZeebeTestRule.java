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

import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.client.ClientProperties;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Properties;
import java.util.function.Supplier;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class ZeebeTestRule extends ExternalResource {
  private final EmbeddedBrokerRule brokerRule;
  private final ClientRule clientRule;
  protected final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

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
                  ClientProperties.BROKER_CONTACTPOINT, brokerRule.getGatewayAddress().toString());
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
  public Statement apply(final Statement base, final Description description) {
    final Statement statement = recordingExporterTestWatcher.apply(base, description);
    return super.apply(statement, description);
  }

  @Override
  protected void after() {
    clientRule.after();
    brokerRule.after();
  }

  public static WorkflowInstanceAssert assertThat(WorkflowInstanceEvent workflowInstance) {
    return WorkflowInstanceAssert.assertThat(workflowInstance);
  }

  public void printWorkflowInstanceEvents(final long key) {
    RecordingExporter.workflowInstanceRecords()
        .withWorkflowInstanceKey(key)
        .forEach(
            event -> {
              System.out.println("> " + event.toJson());
            });
  }
}
