/*
 * Copyright 2016-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.cluster.messaging;

import java.io.IOException;

/** Top level exception for MessagingService failures. */
public class MessagingException extends IOException {

  public MessagingException(final String message) {
    super(message);
  }

  public MessagingException(final String message, final Throwable cause) {
    super(message, cause);
  }

  /** Exception indicating no remote registered remote handler. */
  public static class NoRemoteHandler extends MessagingException {
    public NoRemoteHandler(String subject) {
      super(
          String.format(
              "No remote message handler registered for this message, subject %s", subject));
    }
  }

  /** Exception indicating handler failure. */
  public static class RemoteHandlerFailure extends MessagingException {
    public RemoteHandlerFailure(String message) {
      super(String.format("Remote handler failed to handle message, cause: %s", message));
    }
  }

  /**
   * Exception indicating failure due to invalid message structure such as an incorrect preamble.
   */
  public static class ProtocolException extends MessagingException {
    public ProtocolException() {
      super("Failed to process message due to invalid message structure");
    }
  }
}
