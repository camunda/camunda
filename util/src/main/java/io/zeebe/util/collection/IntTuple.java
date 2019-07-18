/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.collection;

public class IntTuple<R> {
  private int left;
  private R right;

  public IntTuple(int left, R right) {
    this.left = left;
    this.right = right;
  }

  public int getInt() {
    return left;
  }

  public void setInt(int left) {
    this.left = left;
  }

  public R getRight() {
    return right;
  }

  public void setRight(R right) {
    this.right = right;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("<");
    builder.append(left);
    builder.append(", ");
    builder.append(right);
    builder.append(">");
    return builder.toString();
  }
}
