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
package io.camunda.zeebe.protocol.record.intent;

/**
 * This intent is used to track metadata for user-triggered requests that may complete
 * asynchronously. Such requests can be temporarily deferred during processing – for example, due to
 * the need to handle user task listeners.
 */
public enum AsyncRequestMetadataIntent implements Intent {

  /**
   * Emitted when a request is received and its metadata must be preserved for later use, including
   * writing follow-up events and responses.
   */
  RECEIVED((short) 0),

  /**
   * Emitted once the request has been fully processed and the preserved metadata is no longer
   * needed. Acts as a cleanup signal.
   */
  PROCESSED((short) 1);

  private final short value;

  AsyncRequestMetadataIntent(final short value) {
    this.value = value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return RECEIVED;
      case 1:
        return PROCESSED;
      default:
        return UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return true;
  }
}
