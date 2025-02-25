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
package io.camunda.zeebe.client.api.response;

import java.util.List;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.response.BrokerInfo}
 */
@Deprecated
public interface BrokerInfo {
  /**
   * @return the node if of the broker
   */
  int getNodeId();

  /**
   * @return the address host of the broker
   */
  String getHost();

  /**
   * @return the address port of the broker
   */
  int getPort();

  /**
   * @return the address (host+port) of the broker
   */
  String getAddress();

  /**
   * @return the version of the broker
   */
  String getVersion();

  /**
   * @return all partitions of the broker
   */
  List<PartitionInfo> getPartitions();
}
