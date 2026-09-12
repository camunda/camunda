/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/**
 * Payload of {@code GET /.well-known/camunda/webapps}. A {@code null} URL means the webapp is not
 * part of this setup and is omitted from the JSON document.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebappsDiscoveryResponse(@Nullable String operateUrl, @Nullable String tasklistUrl) {}
