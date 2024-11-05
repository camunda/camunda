/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
/**
 * This package contains classes implementing an in memory database for the Zeebe test engine.
 *
 * <p>The database can be obtained via: {@code new InMemoryZeebeDbFactory().createDb(null);}
 *
 * <p><strong>Notes</strong>
 *
 * <ul>
 *   <li>The database must not be used concurrently by multiple threads
 *   <li>The database supports only a single transaction (Technically it's possible to spawn
 *       multiple transactions with "read committed" isolation level. But there is no locking so the
 *       last commit will overwrite previous commits.)
 * </ul>
 */
package io.camunda.zeebe.db.impl.inmemory;
