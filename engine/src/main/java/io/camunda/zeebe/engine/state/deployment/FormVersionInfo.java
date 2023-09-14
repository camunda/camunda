/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.LongValue;

public class FormVersionInfo extends UnpackedObject
    implements DbValue, VersionInfo<FormVersionInfo> {

  private final LongProperty highestVersionProp = new LongProperty("highestVersion", -1L);
  private final ArrayProperty<LongValue> knownVersions =
      new ArrayProperty<>("knownVersions", new LongValue());

  public FormVersionInfo() {
    declareProperty(highestVersionProp).declareProperty(knownVersions);
  }

  /**
   * Gets the highest version of a form. This is the highest version we've ever known. There is no
   * guarantee that a form with this version still exists in the state. It could've been deleted. We
   * need to track this version so we don't ever reuse version numbers after a form has been
   * deleted.
   *
   * @return the highest version we've ever known for this form
   */
  @Override
  public long getHighestVersion() {
    return highestVersionProp.getValue();
  }

  @Override
  public void setHighestVersion(final long version) {
    highestVersionProp.setValue(version);
  }

  @Override
  public ArrayProperty<LongValue> getKnownVersionsProp() {
    return knownVersions;
  }
}
