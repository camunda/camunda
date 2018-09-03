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

import java.util.Collections;
import java.util.List;

public class ClusterCfg implements ConfigurationEntry {
  public static final List<String> DEFAULT_CONTACT_POINTS = Collections.emptyList();

  private List<String> initialContactPoints = DEFAULT_CONTACT_POINTS;

  @Override
  public void init(
      final BrokerCfg globalConfig, final String brokerBase, final Environment environment) {
    applyEnvironment(environment);
  }

  private void applyEnvironment(final Environment environment) {
    environment
        .getList(EnvironmentConstants.ENV_INITIAL_CONTACT_POINTS)
        .ifPresent(v -> initialContactPoints = v);
  }

  public List<String> getInitialContactPoints() {
    return initialContactPoints;
  }

  public void setInitialContactPoints(List<String> initialContactPoints) {
    this.initialContactPoints = initialContactPoints;
  }

  @Override
  public String toString() {
    return "ClusterCfg{" + "initialContactPoints=" + initialContactPoints + '}';
  }
}
