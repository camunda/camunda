/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.system;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.fail;

import io.zeebe.broker.system.configuration.MetricsCfg;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import java.net.Socket;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Rule;
import org.junit.Test;

public class MetricsHttpServerTest {

  @Rule
  public final EmbeddedBrokerRule brokerWithEnabledMetricsHttpServer =
      new EmbeddedBrokerRule(cfg -> cfg.getMetrics().setEnableHttpServer(true));

  @Rule
  public final EmbeddedBrokerRule brokerWithDisabledMetricsHttpServer =
      new EmbeddedBrokerRule(cfg -> cfg.getMetrics().setEnableHttpServer(false));

  @Test
  public void shouldConfigureMetricsHttpServer() {
    MetricsCfg metricsCfg = brokerWithEnabledMetricsHttpServer.getBrokerCfg().getMetrics();
    try (CloseableHttpClient client = HttpClients.createDefault()) {
      final HttpGet request =
          new HttpGet("http://" + metricsCfg.getHost() + ":" + metricsCfg.getPort());
      try (CloseableHttpResponse response = client.execute(request)) {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(EntityUtils.toString(response.getEntity())).contains("zb_broker_info");
      }
    } catch (Exception e) {
      fail("Failed to connect to metrics http server with config: " + metricsCfg, e);
    }

    metricsCfg = brokerWithDisabledMetricsHttpServer.getBrokerCfg().getMetrics();
    try (Socket socket = new Socket(metricsCfg.getHost(), metricsCfg.getPort())) {
      fail("Unexpected to be able to connect to metrics http server with config: " + metricsCfg);
    } catch (Exception e) {
      // expect error
    }
  }
}
