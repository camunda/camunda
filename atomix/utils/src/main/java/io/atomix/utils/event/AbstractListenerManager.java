/*
 * Copyright 2015-present Open Networking Foundation
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
package io.atomix.utils.event;

/** Basis for components which need to export listener mechanism. */
public abstract class AbstractListenerManager<E extends Event, L extends EventListener<E>>
    implements ListenerService<E, L> {

  protected final ListenerRegistry<E, L> listenerRegistry = new ListenerRegistry<>();

  @Override
  public void addListener(final L listener) {
    listenerRegistry.addListener(listener);
  }

  @Override
  public void removeListener(final L listener) {
    listenerRegistry.removeListener(listener);
  }

  /**
   * Posts the specified event to the local event dispatcher.
   *
   * @param event event to be posted; may be null
   */
  protected void post(final E event) {
    listenerRegistry.process(event);
  }
}
