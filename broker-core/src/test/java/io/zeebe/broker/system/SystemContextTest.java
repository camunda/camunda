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

import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.util.sched.clock.ControlledActorClock;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SystemContextTest {

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void shouldThrowExceptionIfNodeIdIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setNodeId(-1);

    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
        "Node id -1 needs to be non negative and smaller then cluster size 1.");

    initSystemContext(brokerCfg);
  }

  @Test
  public void shouldThrowExceptionIfNodeIdIsLargerThenClusterSize() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setNodeId(2);

    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
        "Node id 2 needs to be non negative and smaller then cluster size 1.");

    initSystemContext(brokerCfg);
  }

  @Test
  public void shouldThrowExceptionIfReplicationFactorIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setReplicationFactor(-1);

    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
        "Replication factor -1 needs to be larger then zero and not larger then cluster size 1.");

    initSystemContext(brokerCfg);
  }

  @Test
  public void shouldThrowExceptionIfReplicationFactorIsLargerThenClusterSize() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setReplicationFactor(2);

    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
        "Replication factor 2 needs to be larger then zero and not larger then cluster size 1.");

    initSystemContext(brokerCfg);
  }

  @Test
  public void shouldThrowExceptionIfPartitionsCountIsNegative() {
    // given
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setPartitionsCount(-1);

    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Partition count must not be smaller then 1.");

    initSystemContext(brokerCfg);
  }

  private SystemContext initSystemContext(BrokerCfg brokerCfg) {
    return new SystemContext(brokerCfg, "test", new ControlledActorClock());
  }
}
