/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.util;

import java.util.Map;
import java.util.Properties;
import org.assertj.core.api.Assertions;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.client.ClientProperties;
import io.zeebe.test.ClientRule;
import io.zeebe.test.EmbeddedBrokerRule;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;

public class OperateZeebeRule extends ExternalResource {

  private static final Logger logger = LoggerFactory.getLogger(OperateZeebeRule.class);

  private final EmbeddedBrokerRule brokerRule;
  protected final RecordingExporterTestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();
  private ClientRule clientRule;

  private String prefix;

  public OperateZeebeRule(final String configFileClasspathLocation) {
    brokerRule = new EmbeddedBrokerRule(configFileClasspathLocation);
  }

  @Override
  public void before() {
    this.prefix = TestUtil.createRandomString(10);
    brokerRule.getBrokerCfg().getExporters().stream().filter(ec -> ec.getId().equals("elasticsearch")).forEach(ec -> {
      final Object indexArgs = ec.getArgs().get("index");
      if (indexArgs != null && indexArgs instanceof Map) {
        ((Map) indexArgs).put("prefix", prefix);
      } else {
        Assertions.fail("Unable to configure Elasticsearch exporter");
      }
    });
    start();
  }

  @Override
  public void after() {
    super.after();
    stop();
  }

  @Override
  public Statement apply(Statement base, Description description) {
    Statement statement = this.recordingExporterTestWatcher.apply(base, description);
    return super.apply(statement, description);
  }

  /**
   * Starts the broker and the client. This is blocking and will return once the broker is ready to
   * accept commands.
   *
   * @throws IllegalStateException if no exporter has previously been configured
   */
  public void start() {
    long startTime = System.currentTimeMillis();
    brokerRule.startBroker();
    logger.info("\n====\nBroker startup time: {}\n====\n", (System.currentTimeMillis() - startTime));

    clientRule = new ClientRule(this::newClientProperties);
    clientRule.createClient();
  }

  /** Stops the broker and destroys the client. Does nothing if not started yet. */
  public void stop() {
    brokerRule.stopBroker();

    if (clientRule != null) {
      clientRule.destroyClient();
    }
  }

  /**
   * Returns the current broker configuration.
   *
   * @return current broker configuration
   */
  public BrokerCfg getBrokerConfig() {
    return brokerRule.getBrokerCfg();
  }

  private Properties newClientProperties() {
    final Properties properties = new Properties();
    properties.put(
      ClientProperties.BROKER_CONTACTPOINT,
      getBrokerConfig().getGateway().getNetwork().toSocketAddress().toString());

    return properties;
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public EmbeddedBrokerRule getBrokerRule() {
    return brokerRule;
  }

  public ClientRule getClientRule() {
    return clientRule;
  }
}
