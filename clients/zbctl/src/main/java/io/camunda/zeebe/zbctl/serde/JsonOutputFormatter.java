/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.serde;

import io.avaje.jsonb.JsonType;
import io.avaje.jsonb.Jsonb;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.client.api.response.Topology;
import java.io.OutputStream;

public final class JsonOutputFormatter implements OutputFormatter {

  private final Jsonb jsonb = Jsonb.builder().build();

  private final JsonType<Topology> topologyType = jsonb.type(Topology.class);
  private final JsonType<PublishMessageResponse> publishMessageType =
      jsonb.type(PublishMessageResponse.class);

  @Override
  public void write(final OutputStream output, final Topology topology) {
    topologyType.toJson(topology, output);
  }

  @Override
  public void write(final OutputStream output, final PublishMessageResponse response) {
    publishMessageType.toJson(response, output);
  }
}
