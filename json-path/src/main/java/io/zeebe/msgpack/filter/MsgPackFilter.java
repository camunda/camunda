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

public interface MsgPackFilter {

  /**
   * If the context has elements, the cursor is guaranteed to be on the last element when this
   * method is invoked.
   */
  boolean matches(MsgPackTraversalContext ctx, DirectBuffer filterContext, MsgPackToken value);
}
