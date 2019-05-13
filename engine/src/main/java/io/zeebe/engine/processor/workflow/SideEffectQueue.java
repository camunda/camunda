/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow;

import io.zeebe.engine.processor.SideEffectProducer;
import java.util.ArrayList;
import java.util.List;

public class SideEffectQueue implements SideEffectProducer {
  private final List<SideEffectProducer> sideEffects = new ArrayList<>();

  public void clear() {
    sideEffects.clear();
  }

  @Override
  public boolean flush() {
    if (sideEffects.isEmpty()) {
      return true;
    }

    boolean flushed = true;

    // iterates once over everything, setting the side effect to null to avoid reprocessing if we
    // couldn't flush and this is retried. considered lesser evil than removing from the list and
    // having to shuffle elements around in the list.
    for (int i = 0; i < sideEffects.size(); i++) {
      final SideEffectProducer sideEffect = sideEffects.get(i);

      if (sideEffect != null) {
        if (sideEffect.flush()) {
          sideEffects.set(i, null);
        } else {
          flushed = false;
        }
      }
    }

    // reset list size to 0 if everything was flushed
    if (flushed) {
      sideEffects.clear();
    }

    return flushed;
  }

  public void add(SideEffectProducer sideEffectProducer) {
    sideEffects.add(sideEffectProducer);
  }
}
