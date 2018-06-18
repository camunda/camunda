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
package io.zeebe.gossip.dissemination;

import io.zeebe.gossip.protocol.CustomEvent;
import io.zeebe.gossip.protocol.CustomEventSupplier;
import io.zeebe.util.collection.ReusableObjectList;
import java.util.Iterator;

public class CustomEventSyncResponseSupplier implements CustomEventSupplier {
  private final ReusableObjectList<BufferedEvent<CustomEvent>> customEvents =
      new ReusableObjectList<>(() -> new BufferedEvent<>(new CustomEvent()));

  private final BufferedEventIterator<CustomEvent> viewIterator =
      new BufferedEventIterator<>(false);
  private final BufferedEventIterator<CustomEvent> drainIterator =
      new BufferedEventIterator<>(true);

  private int spreadLimit = 1;

  public CustomEventSyncResponseSupplier() {
    reset();
  }

  public CustomEvent add() {
    return customEvents.add().getEvent();
  }

  public void increaseSpreadLimit() {
    spreadLimit += 1;

    drainIterator.setSpreadLimit(spreadLimit);
  }

  public void reset() {
    spreadLimit = 1;

    drainIterator.setSpreadLimit(1);
  }

  @Override
  public int customEventSize() {
    return customEvents.size();
  }

  @Override
  public Iterator<CustomEvent> customEventViewIterator(int max) {
    viewIterator.wrap(customEvents.iterator(), max);

    return viewIterator;
  }

  @Override
  public Iterator<CustomEvent> customEventDrainIterator(int max) {
    drainIterator.wrap(customEvents.iterator(), max);

    return drainIterator;
  }
}
