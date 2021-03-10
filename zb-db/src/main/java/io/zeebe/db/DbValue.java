/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.db;

import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;

/** The value which should be stored together with a key. */
public interface DbValue extends BufferWriter, BufferReader {}
