/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.errorhandling;

/**
 * Represents error details for Elasticsearch/Opensearch persistence errors when executing batch
 * requests in the exporter.
 *
 * @param message the error message
 * @param type the error type communicated by the search engine
 * @param status the HTTP status code of the error
 */
public record Error(String message, String type, int status) {}
