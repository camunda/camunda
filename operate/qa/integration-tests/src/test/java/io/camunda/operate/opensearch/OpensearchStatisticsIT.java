/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.opensearch;

import io.camunda.operate.elasticsearch.IncidentStatisticsIT;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"camunda.data.secondary-storage.type=opensearch"})
public class OpensearchStatisticsIT extends IncidentStatisticsIT {}
