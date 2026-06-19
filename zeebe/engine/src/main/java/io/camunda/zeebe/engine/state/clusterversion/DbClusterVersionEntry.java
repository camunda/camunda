/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.clusterversion;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;

final class DbClusterVersionEntry extends UnpackedObject implements DbValue {

  private final IntegerProperty lineProp = new IntegerProperty("line", 0);
  private final IntegerProperty ordinalProp = new IntegerProperty("ordinal", 0);

  DbClusterVersionEntry() {
    super(2);
    declareProperty(lineProp).declareProperty(ordinalProp);
  }

  int getLine() {
    return lineProp.getValue();
  }

  int getOrdinal() {
    return ordinalProp.getValue();
  }

  DbClusterVersionEntry set(final int line, final int ordinal) {
    lineProp.setValue(line);
    ordinalProp.setValue(ordinal);
    return this;
  }
}
