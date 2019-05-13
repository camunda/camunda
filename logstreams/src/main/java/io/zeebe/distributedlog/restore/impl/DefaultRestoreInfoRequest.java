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

import io.zeebe.distributedlog.restore.RestoreInfoRequest;

public class DefaultRestoreInfoRequest implements RestoreInfoRequest {
  private long localPosition;
  private long backupPosition;

  public DefaultRestoreInfoRequest() {}

  public DefaultRestoreInfoRequest(long localPosition, long backupPosition) {
    this.localPosition = localPosition;
    this.backupPosition = backupPosition;
  }

  @Override
  public long getLatestLocalPosition() {
    return localPosition;
  }

  public void setLatestLocalPosition(long localPosition) {
    this.localPosition = localPosition;
  }

  @Override
  public long getBackupPosition() {
    return backupPosition;
  }

  public void setBackupPosition(long backupPosition) {
    this.backupPosition = backupPosition;
  }
}
