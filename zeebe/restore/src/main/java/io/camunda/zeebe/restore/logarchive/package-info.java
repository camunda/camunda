/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * Restores partitions from the log archive: folds the bucket into a view per partition, resolves
 * the target checkpoint across partitions, and rebuilds snapshot and journal from it.
 */
@NullMarked
package io.camunda.zeebe.restore.logarchive;

import org.jspecify.annotations.NullMarked;
