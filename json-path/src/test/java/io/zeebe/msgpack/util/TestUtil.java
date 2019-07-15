/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.util;

import io.zeebe.msgpack.query.MsgPackFilterContext;

public class TestUtil {

  public static MsgPackFilterContext generateDefaultInstances(int... filterIds) {

    final MsgPackFilterContext filterInstances = new MsgPackFilterContext(filterIds.length, 10);
    for (int i = 0; i < filterIds.length; i++) {
      filterInstances.appendElement();
      filterInstances.filterId(filterIds[i]);
    }
    return filterInstances;
  }
}
