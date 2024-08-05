/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.plugin.search.header;

/**
 * An object that represents HTTP header that is supposed to be added to a search database HTTP
 * call.
 *
 * @param key - HTTP header key
 * @param value - HTTP header value
 */
public record CustomHeader(String key, String value) {}
