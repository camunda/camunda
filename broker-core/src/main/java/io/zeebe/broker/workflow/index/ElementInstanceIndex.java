/*
 * Zeebe Broker Core
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
package io.zeebe.broker.workflow.index;

import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.io.Serializable;
import org.agrona.collections.Long2ObjectHashMap;

public class ElementInstanceIndex implements Serializable {

  private static final long serialVersionUID = 1L;

  private Long2ObjectHashMap<ElementInstance> instances = new Long2ObjectHashMap<>();

  public ElementInstance newInstance(
      long key, WorkflowInstanceRecord value, WorkflowInstanceIntent state) {
    return newInstance(null, key, value, state);
  }

  public ElementInstance newInstance(
      ElementInstance parent,
      long key,
      WorkflowInstanceRecord value,
      WorkflowInstanceIntent state) {
    final ElementInstance instance = new ElementInstance(key, parent);

    instance.setState(state);
    instance.setValue(value);

    instances.put(key, instance);
    return instance;
  }

  public ElementInstance getInstance(long key) {
    return instances.get(key);
  }

  public void removeInstance(long key) {
    final ElementInstance scopeInstance = instances.remove(key);

    if (scopeInstance != null) {
      scopeInstance.destroy();
    }
  }

  public void shareState(ElementInstanceIndex other) {
    this.instances = other.instances;
  }
}
