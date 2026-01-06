/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import java.util.List;
import java.util.Map;

/**
 * Represents a batch of documents to be archived, containing the finish date and document keys
 * mapped to their respective field names.
 */
public record ArchiveBatch(String finishDate, Map<String, List<String>> ids) {}
