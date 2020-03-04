/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Comparator;
import java.util.List;

@AllArgsConstructor
@Slf4j
@Secured
@Path("/webhooks")
@Component
public class WebhookRestService {
  private final SessionService sessionService;
  private final ConfigurationService configurationService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/")
  public List<String> getAllWebhookNames(@Context ContainerRequestContext requestContext) {
    List<String> sortedWebhookNames = Lists.newArrayList(configurationService.getConfiguredWebhooks().keySet());
    sortedWebhookNames.sort(Comparator.naturalOrder());
    return sortedWebhookNames;
  }
}
