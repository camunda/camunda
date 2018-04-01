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
package io.zeebe.broker.configuration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.zeebe.broker.transport.cfg.TransportComponentCfg;
import io.zeebe.raft.RaftConfiguration;
import org.junit.Rule;
import org.junit.Test;

public class ConfigurationTest
{

    @Rule
    public ConfigurationRule configurationRule = new ConfigurationRule();

    @Test
    @ConfigurationFile("zeebe.test.raft.cfg.toml")
    public void shouldSetRaftConfiguration()
    {
        final RaftConfiguration config = configurationRule.getComponent("network", TransportComponentCfg.class).raft;

        assertThat(config.getHeartbeatIntervalMs()).isEqualTo(1234);
        assertThat(config.getElectionIntervalMs()).isEqualTo(2345);
    }

}
