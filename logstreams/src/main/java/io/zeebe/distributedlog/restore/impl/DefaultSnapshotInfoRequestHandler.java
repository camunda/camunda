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
package io.zeebe.distributedlog.restore.impl;

import io.zeebe.distributedlog.restore.RestoreServer.SnapshotInfoRequestHandler;
import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.spi.SnapshotController;
import org.slf4j.Logger;

public class DefaultSnapshotInfoRequestHandler implements SnapshotInfoRequestHandler {

  private static final Logger LOG = Loggers.PROCESSOR_LOGGER;
  private final SnapshotController[] controllers;

  public DefaultSnapshotInfoRequestHandler(SnapshotController... controllers) {
    this.controllers = controllers;
  }

  @Override
  public Integer onSnapshotInfoRequest(Void request) {
    int numSnapshotsToReplicate = 0;
    for (SnapshotController controller : controllers) {
      if (controller.getValidSnapshotsCount() > 0) {
        numSnapshotsToReplicate++;
      }
    }
    LOG.info("Number of snapshots to replicate {}", numSnapshotsToReplicate);
    return numSnapshotsToReplicate;
  }
}
