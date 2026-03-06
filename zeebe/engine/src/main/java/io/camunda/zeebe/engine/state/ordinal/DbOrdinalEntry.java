/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.ordinal;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;

final class DbOrdinalEntry extends UnpackedObject implements DbValue {

  private final IntegerProperty ordinalProperty = new IntegerProperty("ordinal", 0);
  private final LongProperty dateTimeProperty = new LongProperty("dateTime", 0L);

  DbOrdinalEntry() {
    super(2);
    declareProperty(ordinalProperty).declareProperty(dateTimeProperty);
  }

  int getOrdinal() {
    return ordinalProperty.getValue();
  }

  long getDateTime() {
    return dateTimeProperty.getValue();
  }

  DbOrdinalEntry set(final int ordinal, final long dateTimeMillis) {
    ordinalProperty.setValue(ordinal);
    dateTimeProperty.setValue(dateTimeMillis);
    return this;
  }
}
