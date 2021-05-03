/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.msgpack;

import io.camunda.zeebe.msgpack.property.LongProperty;

public final class DefaultValuesPOJO extends UnpackedObject {

  protected final LongProperty defaultValueProperty;
  protected final LongProperty noDefaultValueProperty = new LongProperty("noDefaultValueProp");

  public DefaultValuesPOJO(final long defaultValue) {
    defaultValueProperty = new LongProperty("defaultValueProp", defaultValue);

    declareProperty(defaultValueProperty).declareProperty(noDefaultValueProperty);
  }

  public long getDefaultValueProperty() {
    return defaultValueProperty.getValue();
  }

  public void setDefaultValueProperty(final long value) {
    defaultValueProperty.setValue(value);
  }

  public long getNoDefaultValueProperty() {
    return noDefaultValueProperty.getValue();
  }

  public void setNoDefaultValueProperty(final long value) {
    noDefaultValueProperty.setValue(value);
  }
}
