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
package io.zeebe.distributedlog.impl;

import io.atomix.primitive.Synchronous;
import io.zeebe.distributedlog.AsyncDistributedLogstream;
import io.zeebe.distributedlog.DistributedLogstream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BlockingDistributedLogstream extends Synchronous<AsyncDistributedLogstream>
    implements DistributedLogstream {

  private final DistributedLogstreamProxy distributedLogstreamProxy;
  private final long timeout;

  public BlockingDistributedLogstream(
      DistributedLogstreamProxy distributedLogstreamProxy, long timeout) {
    super(distributedLogstreamProxy);
    this.distributedLogstreamProxy = distributedLogstreamProxy;
    this.timeout = timeout;
  }

  @Override
  public long append(String partition, String nodeId, long commitPosition, byte[] blockBuffer) {
    try {
      return distributedLogstreamProxy
          .append(partition, nodeId, commitPosition, blockBuffer)
          .get(timeout, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (ExecutionException e) {
      e.printStackTrace();
    } catch (TimeoutException e) {
      e.printStackTrace();
    }
    // Append failed
    return -1;
  }

  @Override
  public void claimLeaderShip(String partition, String nodeId, long leaderTerm) {
    try {
      distributedLogstreamProxy
          .claimLeaderShip(partition, nodeId, leaderTerm)
          .get(timeout, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (ExecutionException e) {
      e.printStackTrace();
    } catch (TimeoutException e) {
      e.printStackTrace();
    }
  }

  @Override
  public AsyncDistributedLogstream async() {
    return distributedLogstreamProxy;
  }
}
