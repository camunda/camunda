/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import java.time.Duration;

/** How long a record written to the zeebe-record indices takes to become searchable. */
public interface ZeebeRecordRefreshIntervalReader {

  Duration DEFAULT_REFRESH_INTERVAL = Duration.ofSeconds(1);

  /** The longest refresh interval of the given zeebe-record index alias, or the default. */
  Duration getRefreshInterval(String indexAlias);
}
