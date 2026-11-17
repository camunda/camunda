/*
 * Copyright 2018-present Open Networking Foundation
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

import com.google.common.util.concurrent.MoreExecutors;
import io.atomix.utils.net.Address;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/**
 * Service for unreliable, fire-and-forget messaging between nodes.
 *
 * <p>This service makes no guarantee regarding the reliability or order of messages: a send may be
 * dropped without notice, so callers must be able to tolerate loss and retry.
 *
 * <p>A subject carries at most one listener, mirroring {@link MessagingService#registerHandler}.
 */
public interface UnicastService {

  /**
   * Sends the given message to the listener registered for the given subject on the peer at the
   * given address.
   *
   * <p>This service makes no guarantee regarding the reliability or order of delivery of the
   * message.
   *
   * @param address the address to which to unicast the message
   * @param subject the message subject
   * @param message the message to send
   */
  void unicast(Address address, String subject, byte[] message);

  /**
   * Registers the listener for the given subject, called inline on the thread that dispatches the
   * received message.
   *
   * <p>That thread belongs to the service (the receiving event loop, for {@code
   * NettyUnicastService}), so a listener that blocks stalls the processing of further incoming
   * messages. Register with an {@link Executor} instead if the listener may block.
   *
   * @param subject the message subject
   * @param listener the listener to register
   */
  default void addListener(final String subject, final BiConsumer<Address, byte[]> listener) {
    addListener(subject, listener, MoreExecutors.directExecutor());
  }

  /**
   * Registers the listener for the given subject, replacing any listener already registered for it.
   *
   * <p>The replacement is silent, as it is for {@link MessagingService#registerHandler}. A subject
   * carries at most one listener, so a second registration takes over delivery rather than adding
   * to it.
   *
   * @param subject the message subject
   * @param listener the listener to register
   * @param executor an executor with which to call the listener
   */
  void addListener(String subject, BiConsumer<Address, byte[]> listener, Executor executor);

  /**
   * Removes the given listener from the given subject, if it is the one currently registered.
   *
   * <p>Passing a listener that has since been replaced does nothing, so a stale reference cannot
   * unregister its replacement.
   *
   * @param subject the message subject
   * @param listener the listener to remove
   */
  void removeListener(String subject, BiConsumer<Address, byte[]> listener);
}
