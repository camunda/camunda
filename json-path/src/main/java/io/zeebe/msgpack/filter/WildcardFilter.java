/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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
