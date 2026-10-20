/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import io.camunda.zeebe.eventplane.store.ObjectNames;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A consumer group, as an admin or the service writes it at {@link ObjectNames#group}:
 *
 * <pre>{@code
 * {"version":1,"name":"g","subscription":[{"cluster":"c","tenant":"*"}],"start":"earliest"}
 * }</pre>
 *
 * @param name the group's name
 * @param subscription the logs it reads
 * @param start where it starts in a log that it has no cursor for
 */
public record GroupDefinition(String name, List<Subscription> subscription, Start start) {

  public GroupDefinition {
    if (name.isEmpty()) {
      throw new IllegalArgumentException("Expected a group name, but got an empty one");
    }
    subscription = List.copyOf(subscription);
  }

  /** The group as JSON. */
  public byte[] toJson() {
    final var root = JsonFields.versioned().put("name", name);
    final var array = root.putArray("subscription");
    subscription.forEach(
        entry -> array.addObject().put("cluster", entry.cluster()).put("tenant", entry.tenant()));
    root.put("start", start.jsonName());
    return JsonFields.write(root);
  }

  /**
   * Reads a group. Fields that it doesn't know are skipped.
   *
   * @throws InvalidObjectJsonException if the JSON isn't that of a group
   * @throws IOException if the bytes aren't JSON
   */
  public static GroupDefinition fromJson(final byte[] json) throws IOException {
    final var fields = JsonFields.parse(json);
    final var subscription = new ArrayList<Subscription>();
    for (final var entry : fields.objects("subscription")) {
      subscription.add(new Subscription(entry.text("cluster"), entry.text("tenant")));
    }
    final var start = fields.text("start");
    try {
      return new GroupDefinition(fields.text("name"), subscription, Start.ofJsonName(start));
    } catch (final IllegalArgumentException e) {
      throw new InvalidObjectJsonException("start", "'earliest' or 'latest'", start, e);
    }
  }
}
