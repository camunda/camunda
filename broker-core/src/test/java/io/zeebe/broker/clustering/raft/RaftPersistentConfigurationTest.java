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
package io.zeebe.broker.clustering.raft;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.zeebe.broker.clustering.base.raft.RaftConfigurationMetadata;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.Protocol;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

public class RaftPersistentConfigurationTest {
  @Rule public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  @Test
  public void shouldPersistConfigurationAsJSON() throws IOException {
    final int partitionId = Protocol.START_PARTITION_ID;

    final BrokerCfg brokerCfg = brokerRule.getBroker().getBrokerContext().getBrokerConfiguration();
    final ObjectReader jsonReader = new ObjectMapper().readerFor(RaftConfigurationMetadata.class);

    final String dataDirectory = brokerCfg.getData().getDirectories().get(0);
    final File configFile =
        new File(String.format("%s/partition-%d/partition.json", dataDirectory, partitionId));

    final RaftConfigurationMetadata persisted = jsonReader.readValue(configFile);
    assertThat(persisted.getPartitionId()).isEqualTo(partitionId);
  }
}
