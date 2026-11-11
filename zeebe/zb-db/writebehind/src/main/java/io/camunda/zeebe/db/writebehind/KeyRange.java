/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import org.jspecify.annotations.Nullable;

/**
 * A range of keys.
 *
 * @param from the first key of the range
 * @param to the first key after the range, or null if the range goes on to the last key
 */
record KeyRange<K>(K from, @Nullable K to) {}
