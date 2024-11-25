/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import java.util.Map;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;

public interface SearchDatabaseCallbackDelegate
    extends AfterAllCallback, BeforeAllCallback, AfterEachCallback {

  ExporterConfiguration getConfigWithConnectionDetails(ConnectionTypes connectionType);

  Map<ConnectionTypes, SearchClientAdapter> contextAdapterRegistration();
}
