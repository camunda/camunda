/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code long} or {@code int} field of a {@link MsgpackPojo} that has no default, like a
 * {@code LongProperty} or {@code IntegerProperty} declared without one. The field initializer is
 * the sentinel for the unset state instead of a default, and without one it is {@code 0}:
 *
 * <pre>{@code
 * @MsgpackRequired long processInstanceKey;  // unset while 0: no key is 0, partitions start at 1
 * @MsgpackRequired int version = -1;          // unset while -1
 * }</pre>
 *
 * <p>A new instance and one after {@code reset()} are both unset, as the sentinel is the default.
 *
 * <p>The field stays a primitive, so reading does not allocate and there is no holder to follow.
 * Writing, or asking for the length, while the field holds its sentinel fails like an unset legacy
 * property, and so does reading a message without the key, or with the sentinel as its value. The
 * sentinel must therefore be a value the property never holds, e.g. {@code -1} for a key: a real
 * value equal to it cannot be written.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface MsgpackRequired {}
