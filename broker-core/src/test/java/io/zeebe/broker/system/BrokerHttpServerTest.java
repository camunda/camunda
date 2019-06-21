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

import io.zeebe.broker.system.configuration.SocketBindingCfg;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import java.io.IOException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class BrokerHttpServerTest {

  @ClassRule public static final EmbeddedBrokerRule RULE = new EmbeddedBrokerRule();

  private static String baseUrl;

  @BeforeClass
  public static void setUp() {
    final SocketBindingCfg monitoringApi = RULE.getBrokerCfg().getNetwork().getMonitoringApi();
    baseUrl = String.format("http://%s:%d", monitoringApi.getHost(), monitoringApi.getPort());
  }

  @Test
  public void shouldGetMetrics() throws IOException {
    final String url = baseUrl + "/metrics";

    try (CloseableHttpClient client = HttpClients.createDefault()) {
      final HttpGet request = new HttpGet(url);
      try (CloseableHttpResponse response = client.execute(request)) {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(EntityUtils.toString(response.getEntity())).contains("jvm_info");
      }
    }
  }

  @Test
  public void shouldGetReadyStatus() throws IOException {
    final String url = baseUrl + "/ready";

    try (CloseableHttpClient client = HttpClients.createDefault()) {
      final HttpGet request = new HttpGet(url);
      try (CloseableHttpResponse response = client.execute(request)) {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(204);
      }
    }
  }
}
