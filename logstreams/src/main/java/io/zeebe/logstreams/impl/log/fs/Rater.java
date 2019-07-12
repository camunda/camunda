/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log.fs;

public class Rater {
  private Runnable onIncrement;
  private int count;
  private int rate;

  public Rater(int rate, Runnable onIncrement) {
    this.rate = rate;
    this.onIncrement = onIncrement;
  }

  public void mark(int increment) {
    count += increment;

    if (count > rate) {
      onIncrement.run();
      count = 0;
    }
  }
}
