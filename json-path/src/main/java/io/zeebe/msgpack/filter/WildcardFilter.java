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
package io.zeebe.msgpack.filter;

import io.zeebe.msgpack.query.MsgPackTraversalContext;
import io.zeebe.msgpack.spec.MsgPackToken;
import org.agrona.DirectBuffer;

public class WildcardFilter implements MsgPackFilter {

  @Override
  public boolean matches(
      MsgPackTraversalContext ctx, DirectBuffer filterContext, MsgPackToken value) {
    if (ctx.hasElements() && ctx.isMap()) {
      return ctx.currentElement() % 2 != 0; // don't match map keys
    }

    return true;
  }
}
