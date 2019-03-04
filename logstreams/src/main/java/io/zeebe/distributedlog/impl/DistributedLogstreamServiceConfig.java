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

import io.atomix.primitive.service.ServiceConfig;
import io.zeebe.util.ByteValue;

/* Define any configuration parameters needed for the DefaultDistributedLogstreamService */
public class DistributedLogstreamServiceConfig extends ServiceConfig {
  private int logSegmentSize = (int) new ByteValue("512M").toBytes();

  public DistributedLogstreamServiceConfig withLogSegmentSize(int size) {
    this.logSegmentSize = size;
    return this;
  }

  public int getLogSegmentSize() {
    return logSegmentSize;
  }
}
