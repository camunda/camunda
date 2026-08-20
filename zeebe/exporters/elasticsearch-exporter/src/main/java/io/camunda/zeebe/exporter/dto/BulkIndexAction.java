/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A bulk request consists of multiple metadata/data pairs. The data is the document you wish to
 * create, update, etc., and the metadata (also known as action) defines how the document will be
 * affected (e.g. update, create, index, etc.).
 */
public record BulkIndexAction(
    @JsonProperty("_index") String index, @JsonProperty("_id") String id, String routing) {}
