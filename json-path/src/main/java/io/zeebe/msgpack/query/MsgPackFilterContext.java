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
package io.zeebe.msgpack.query;

import org.agrona.BitUtil;

public class MsgPackFilterContext extends AbstractDynamicContext {

  protected static final int FILTER_ID_OFFSET = 0;

  protected static final int STATIC_ELEMENT_SIZE = BitUtil.SIZE_OF_INT;

  public MsgPackFilterContext(int capacity, int dynamicContextSize) {
    super(capacity, STATIC_ELEMENT_SIZE, dynamicContextSize);
  }

  public int filterId() {
    return cursorView.getInt(FILTER_ID_OFFSET);
  }

  public void filterId(int filterId) {
    cursorView.putInt(FILTER_ID_OFFSET, filterId);
  }
}
