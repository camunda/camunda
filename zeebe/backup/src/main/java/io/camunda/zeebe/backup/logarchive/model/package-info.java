/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * JSON model of the objects a log archive writes to the bucket (spec section 9): commit entries,
 * index chunks, roots, the latest hint and snapshot descriptors. Independent of the protocol
 * classes. All objects are serialized by {@link
 * io.camunda.zeebe.backup.logarchive.model.LogArchiveJson}.
 */
@NullMarked
package io.camunda.zeebe.backup.logarchive.model;

import org.jspecify.annotations.NullMarked;
