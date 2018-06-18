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
package io.zeebe.client.api.subscription;

/**
 * Represents an active job worker that perfors jobs of a certain type. While a registration is
 * open, the client continuously receives jobs from the broker and hands them to a registered {@link
 * JobHandler}.
 */
public interface JobWorker {
  /**
   * @return true if this registration is currently active and work items are being received for it
   */
  boolean isOpen();

  /**
   * @return true if this registration is not open and is not in the process of opening or closing
   */
  boolean isClosed();

  /**
   * Closes this registration and stops receiving new work items. Blocks until all previously
   * received items have been handed to the worker.
   */
  void close();
}
