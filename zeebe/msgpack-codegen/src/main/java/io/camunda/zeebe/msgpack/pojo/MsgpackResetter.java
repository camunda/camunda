/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import io.camunda.zeebe.msgpack.Recyclable;

/**
 * Restores the defaults of a {@code T}: the field initializers of its {@link MsgpackPojo} class.
 * Generated, like the {@link MsgpackCodec}, and stateless.
 */
public interface MsgpackResetter<T extends Recyclable> {

  void reset(T target);
}
