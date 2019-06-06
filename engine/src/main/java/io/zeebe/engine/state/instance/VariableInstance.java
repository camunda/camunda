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
package io.zeebe.engine.state.instance;

import io.zeebe.db.DbValue;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.BinaryProperty;
import io.zeebe.msgpack.property.LongProperty;
import org.agrona.DirectBuffer;

public class VariableInstance extends UnpackedObject implements DbValue {

  private final LongProperty keyProp = new LongProperty("key");
  private final BinaryProperty valueProp = new BinaryProperty("value");

  public VariableInstance() {
    this.declareProperty(keyProp).declareProperty(valueProp);
  }

  public VariableInstance setKey(long key) {
    keyProp.setValue(key);
    return this;
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public VariableInstance setValue(DirectBuffer value, int offset, int length) {
    valueProp.setValue(value, offset, length);
    return this;
  }

  public DirectBuffer getValue() {
    return valueProp.getValue();
  }
}
