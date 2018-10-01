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
package io.zeebe.gateway.impl.broker.response;

public class BrokerResponse<T> {

  private final boolean isResponse;
  private final T response;
  private final int partitionId;
  private final long key;

  protected BrokerResponse() {
    this.isResponse = false;
    this.response = null;
    this.partitionId = -1;
    this.key = -1;
  }

  public BrokerResponse(T response) {
    this(response, -1, -1);
  }

  public BrokerResponse(T response, int partitionId, long key) {
    this.isResponse = true;
    this.response = response;
    this.partitionId = partitionId;
    this.key = key;
  }

  public boolean isError() {
    return false;
  }

  public BrokerError getError() {
    return null;
  }

  public boolean isRejection() {
    return false;
  }

  public BrokerRejection getRejection() {
    return null;
  }

  public boolean isResponse() {
    return isResponse;
  }

  public T getResponse() {
    return response;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public long getKey() {
    return key;
  }

  @Override
  public String toString() {
    return "BrokerResponse{"
        + "isResponse="
        + isResponse
        + ", response="
        + response
        + ", partitionId="
        + partitionId
        + ", key="
        + key
        + '}';
  }
}
