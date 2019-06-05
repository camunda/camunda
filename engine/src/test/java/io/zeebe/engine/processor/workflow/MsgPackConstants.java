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

import io.zeebe.test.util.MsgPackUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MsgPackConstants {

  public static final String JSON_DOCUMENT = "{'string':'value', 'jsonObject':{'testAttr':'test'}}";
  public static final String OTHER_DOCUMENT = "{'string':'bar', 'otherObject':{'testAttr':'test'}}";

  public static final DirectBuffer MSGPACK_VARIABLES;
  public static final byte[] OTHER_VARIABLES;

  static {
    MSGPACK_VARIABLES = new UnsafeBuffer(MsgPackUtil.asMsgPackReturnArray(JSON_DOCUMENT));
    OTHER_VARIABLES = MsgPackUtil.asMsgPackReturnArray(OTHER_DOCUMENT);
  }
}
