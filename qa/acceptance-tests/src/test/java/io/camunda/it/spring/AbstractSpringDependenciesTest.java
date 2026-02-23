/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

abstract class AbstractSpringDependenciesTest {
  private final Map<String, Set<String>> beansGraph = new HashMap<>();

  void assertThatNoDependenciesBetween(final String from, final String to) {
    assertBeanExists(from);
    assertBeanExists(to);
    assertThat(findAnyPath(from, to)).isNull();
  }

  void assertBeanExists(final String beanName) {
    assertThat(beansGraph).containsKey(beanName);
  }

  private List<String> findAnyPath(final String start, final String target) {
    final var queue = new LinkedList<List<String>>();
    final var visited = new HashSet<String>();
    queue.add(List.of(start));
    while (!queue.isEmpty()) {
      final var path = queue.poll();
      final var last = path.getLast();

      if (last.equals(target)) {
        return path;
      }

      if (visited.contains(last)) {
        continue;
      }
      visited.add(last);

      for (final String neighbor : beansGraph.getOrDefault(last, Set.of())) {
        final var newPath = new ArrayList<>(path);
        newPath.add(neighbor);
        queue.add(newPath);
      }
    }

    return null;
  }

  void fetchBeansGraph(final URI actuatorBeansURI) {
    final var restTemplate = new RestTemplate();
    // this is needed to allow parsing to JsonNode
    restTemplate.getMessageConverters().addFirst(new MappingJackson2HttpMessageConverter());

    final var response = restTemplate.getForEntity(actuatorBeansURI, JsonNode.class);
    final JsonNode beans = response.getBody().get("contexts").elements().next().get("beans");
    beans
        .properties()
        .forEach(
            entry -> {
              final String beanName = entry.getKey();
              final JsonNode deps = entry.getValue().get("dependencies");
              if (deps != null && deps.isArray()) {
                final Set<String> depSet = new HashSet<>();
                deps.forEach(dep -> depSet.add(dep.asText()));
                beansGraph.put(beanName, depSet);
              }
            });
  }
}
