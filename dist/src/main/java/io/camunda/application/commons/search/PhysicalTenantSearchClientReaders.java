/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.search.clients.reader.SearchClientReaders;
import java.util.Map;

/**
 * Need a typed class as injecting Map<String, SearchClientReaders> from multiple sources causes
 * confusion as instead spring will look for beans of type SearchClientReaders and inject them into
 * the map with the key being the bean name. By instead injecting a
 * List<PhysicalTenantSearchClientReaders> all the maps will be injected where the key maintains its
 * value as the physical tenant id.
 */
public record PhysicalTenantSearchClientReaders(
    Map<String, SearchClientReaders> readersByPhysicalTenant) {}
