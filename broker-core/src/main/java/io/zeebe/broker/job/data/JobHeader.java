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
package io.zeebe.broker.job.data;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public class JobHeader extends UnpackedObject {
  private final StringProperty keyProp = new StringProperty("key");
  private final StringProperty valueProp = new StringProperty("value");

  public JobHeader() {
    this.declareProperty(keyProp).declareProperty(valueProp);
  }

  public DirectBuffer getKey() {
    return keyProp.getValue();
  }

  public JobHeader setKey(String key) {
    this.keyProp.setValue(key);
    return this;
  }

  public DirectBuffer getValue() {
    return valueProp.getValue();
  }

  public JobHeader setValue(String value) {
    this.valueProp.setValue(value);
    return this;
  }
}
