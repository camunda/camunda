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
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.test.EmbeddedBrokerRule;

public class OperateZeebeBrokerRule extends EmbeddedBrokerRule {

  private String prefix;

  private PrefixConfigurator prefixConfigurator;

  public OperateZeebeBrokerRule(final String configFileClasspathLocation) {
    super(configFileClasspathLocation, new PrefixConfigurator());
    final Consumer<BrokerCfg>[] configurators = super.configurators;
    for (Consumer<BrokerCfg> brokerCfg: configurators) {
      if (brokerCfg instanceof PrefixConfigurator) {
        this.prefixConfigurator = (PrefixConfigurator)brokerCfg;
        break;
      }
    }
  }

  @Override
  protected void before() {
    this.prefix = TestUtil.createRandomString(10);
    prefixConfigurator.setPrefix(prefix);
    super.before();
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public static class PrefixConfigurator implements Consumer<BrokerCfg> {

    private String prefix;

    public PrefixConfigurator() {
    }

    public PrefixConfigurator(String prefix) {
      this.prefix = prefix;
    }

    public String getPrefix() {
      return prefix;
    }

    public void setPrefix(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public void accept(BrokerCfg brokerCfg) {
      brokerCfg.getExporters().stream().filter(ec -> ec.getId().equals("elasticsearch")).forEach(ec -> {
        final Object indexArgs = ec.getArgs().get("index");
        if (indexArgs != null && indexArgs instanceof Map) {
          ((Map) indexArgs).put("prefix", prefix);
        } else {
          Assertions.fail("Unable to configure Elasticsearch exporter");
        }
      });
    }
  };
}
