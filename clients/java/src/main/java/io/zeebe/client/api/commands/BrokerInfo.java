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
package io.zeebe.client.api.commands;

import java.util.List;

public interface BrokerInfo {
  /** @return the address host of the broker */
  String getHost();

  /** @return the address port of the broker */
  int getPort();

  /** @return the address (host+port) of the broker */
  String getAddress();

  /** @return all partitions of the broker */
  List<PartitionInfo> getPartitions();
}
