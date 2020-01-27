/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.api.response;

import java.util.List;

public interface Topology {
  /** @return all (known) brokers of the cluster */
  List<BrokerInfo> getBrokers();

  /** @return the size of the Zeebe broker cluster */
  int getClusterSize();

  /** @return the configured number of partitions */
  int getPartitionsCount();

  /** @return the configured replication factor for every partition */
  int getReplicationFactor();

  /** @return the gateway version or an empty string if none was found */
  String getGatewayVersion();
}
