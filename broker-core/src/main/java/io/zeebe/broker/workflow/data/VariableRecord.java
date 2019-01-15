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
package io.zeebe.broker.workflow.data;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.BinaryProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public class VariableRecord extends UnpackedObject {

  private final StringProperty nameProp = new StringProperty("name");
  private final BinaryProperty valueProp = new BinaryProperty("value");
  private final LongProperty scopeInstanceKeyProp = new LongProperty("scopeInstanceKey");

  public VariableRecord() {
    this.declareProperty(nameProp).declareProperty(valueProp).declareProperty(scopeInstanceKeyProp);
  }

  public DirectBuffer getName() {
    return nameProp.getValue();
  }

  public VariableRecord setName(DirectBuffer name) {
    this.nameProp.setValue(name);
    return this;
  }

  public DirectBuffer getValue() {
    return valueProp.getValue();
  }

  public VariableRecord setValue(DirectBuffer value) {
    this.valueProp.setValue(value);
    return this;
  }

  public long getScopeInstanceKey() {
    return scopeInstanceKeyProp.getValue();
  }

  public VariableRecord setScopeInstanceKey(long scopeInstanceKey) {
    this.scopeInstanceKeyProp.setValue(scopeInstanceKey);
    return this;
  }
}
