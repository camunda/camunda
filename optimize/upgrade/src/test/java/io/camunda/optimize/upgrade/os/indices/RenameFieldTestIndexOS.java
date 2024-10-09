/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.os.indices;

import io.camunda.optimize.upgrade.db.indices.RenameFieldTestIndex;
import java.io.IOException;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.IndexSettings.Builder;

public class RenameFieldTestIndexOS extends RenameFieldTestIndex<Builder> {

  @Override
  public IndexSettings.Builder addStaticSetting(
      final String key, final int value, final IndexSettings.Builder indexSettingsBuilder)
      throws IOException {
    return indexSettingsBuilder.numberOfShards(Integer.toString(value));
  }
}
