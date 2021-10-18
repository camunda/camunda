/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared;

/**
 * A fixed set of Spring profiles. Some are application specific (broker, gateway), and others
 * environment specific (dev, test ,prod). Here to avoid littering the code base with different hard
 * coded strings, leading to potential errors.
 *
 * <p>You can activate application & environment profiles together, but should avoid activating two
 * of the same category.
 */
public enum Profile {
  // application specific profiles
  BROKER("broker"),
  GATEWAY("gateway"),

  // environment profiles
  TEST("test"),
  DEVELOPMENT("dev"),
  PRODUCTION("prod");

  private final String id;

  Profile(final String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return "Profiles{ordinal='" + ordinal() + "', name='" + name() + "', id='" + id + '\'' + "}";
  }
}
