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
package io.zeebe.broker.system.configuration;

public class SocketBindingAtomixCfg extends SocketBindingCfg {
  private String receiveBufferSize = "8M";

  public static final int DEFAULT_PORT = 26505;

  public SocketBindingAtomixCfg() {
    port = DEFAULT_PORT;
  }

  @Override
  public void applyDefaults(NetworkCfg networkCfg) {
    super.applyDefaults(networkCfg);
  }

  public String getReceiveBufferSize() {
    return receiveBufferSize;
  }

  public void setReceiveBufferSize(String receiveBufferSize) {
    this.receiveBufferSize = receiveBufferSize;
  }

  @Override
  public String toString() {
    return "SocketBindingAtomixCfg{"
        + "receiveBufferSize='"
        + receiveBufferSize
        + '\''
        + ", host='"
        + host
        + '\''
        + ", port="
        + port
        + ", sendBufferSize='"
        + sendBufferSize
        + '\''
        + '}';
  }
}
