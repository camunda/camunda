/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack;

import io.zeebe.msgpack.property.LongProperty;

public class DefaultValuesPOJO extends UnpackedObject {

  protected LongProperty defaultValueProperty;
  protected LongProperty noDefaultValueProperty = new LongProperty("noDefaultValueProp");

  public DefaultValuesPOJO(long defaultValue) {
    defaultValueProperty = new LongProperty("defaultValueProp", defaultValue);

    this.declareProperty(defaultValueProperty).declareProperty(noDefaultValueProperty);
  }

  public long getDefaultValueProperty() {
    return defaultValueProperty.getValue();
  }

  public void setDefaultValueProperty(long value) {
    this.defaultValueProperty.setValue(value);
  }

  public long getNoDefaultValueProperty() {
    return noDefaultValueProperty.getValue();
  }

  public void setNoDefaultValueProperty(long value) {
    this.noDefaultValueProperty.setValue(value);
  }
}
