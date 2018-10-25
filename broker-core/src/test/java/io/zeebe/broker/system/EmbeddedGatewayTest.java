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

import static org.assertj.core.api.AssertionsForClassTypes.fail;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.junit.Rule;
import org.junit.Test;

public class EmbeddedGatewayTest {

  @Rule
  public final EmbeddedBrokerRule brokerWithEnabledGateway =
      new EmbeddedBrokerRule(cfg -> cfg.getGateway().setEnable(true));

  @Rule
  public final EmbeddedBrokerRule brokerWithDisabledGateway =
      new EmbeddedBrokerRule(cfg -> cfg.getGateway().setEnable(false));

  @Test
  public void shouldConfigureGateway() {
    InetSocketAddress address = brokerWithEnabledGateway.getGatewayAddress().toInetSocketAddress();
    try (Socket socket = new Socket(address.getHostName(), address.getPort())) {
      // expect no error
    } catch (Exception e) {
      fail("Failed to connect to gateway with address: " + address, e);
    }

    address = brokerWithDisabledGateway.getGatewayAddress().toInetSocketAddress();
    try (Socket socket = new Socket(address.getHostName(), address.getPort())) {
      fail("Unexpected to be able to connect to gateway with address: " + address);
    } catch (Exception e) {
      // expect error
    }
  }
}
