/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * Binary codec for log archive objects (format v1). Independent of the journal and raft classes:
 * callers map journal entries into {@link
 * io.camunda.zeebe.backup.logarchive.format.LogArchiveFrame}.
 */
package io.camunda.zeebe.backup.logarchive.format;
