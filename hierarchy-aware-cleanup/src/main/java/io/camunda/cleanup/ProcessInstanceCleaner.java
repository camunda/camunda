/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.cleanup;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class ProcessInstanceCleaner {
  private static final Logger LOG = LoggerFactory.getLogger(ProcessInstanceCleaner.class);
  private final CamundaClient camundaClient;
  private final Executor executor;
  private final Duration relaxedRetentionPolicy;
  private final AtomicReference<String> parentsEndCursor = new AtomicReference<>(null);
  private final AtomicReference<String> orphansEndCursor = new AtomicReference<>(null);
  private final CloseableHttpClient httpClient = HttpClients.createSystem();

  public ProcessInstanceCleaner(final ProcessInstanceCleanerConfiguration configuration) {
    camundaClient = configuration.camundaClient();
    executor = configuration.executor();
    relaxedRetentionPolicy = configuration.relaxedRetentionPolicy();
  }

  @Scheduled(fixedDelay = 10000L)
  public void clean() {
    cleanParents();
    cleanOrphans();
  }

  private void cleanParents() {
    LOG.debug("Killing parents process started");
    if (parentsEndCursor.get() != null) {
      parentsEndCursor.set(findParents(p -> p.after(parentsEndCursor.get()).limit(100)));
    } else {
      parentsEndCursor.set(findParents(p -> p.limit(100)));
    }
    LOG.debug("Killing parents process finished");
  }

  private void cleanOrphans() {
    LOG.debug("Killing orphan process started");
    if (orphansEndCursor.get() != null) {
      orphansEndCursor.set(findOrphans(p -> p.after(orphansEndCursor.get()).limit(100)));
    } else {
      orphansEndCursor.set(findOrphans(p -> p.limit(100)));
    }
    LOG.debug("Killing orphan process finished");
  }

  private String findParents(final Consumer<SearchRequestPage> page) {
    // search for process instances that have a parent, are completed or canceled and already older
    // than expected
    final SearchResponse<ProcessInstance> searchResponse =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.parentProcessInstanceKey(l -> l.exists(false))
                        .state(
                            s ->
                                s.in(
                                    ProcessInstanceState.COMPLETED,
                                    ProcessInstanceState.TERMINATED))
                        .endDate(d -> d.lt(OffsetDateTime.now().minus(relaxedRetentionPolicy))))
            .sort(s -> s.endDate().asc())
            .page(page)
            .execute();
    if (searchResponse.items().isEmpty()) {
      return null;
    } else {
      searchResponse
          .items()
          .forEach(
              processInstance -> {
                LOG.info(
                    "Handling parent process instance {}", processInstance.getProcessInstanceKey());
                executor.execute(
                    () -> treatOrphan(processInstance.getProcessInstanceKey(), p -> p.limit(100)));
                executor.execute(
                    () ->
                        deleteProcessInstance(
                            String.valueOf(processInstance.getProcessInstanceKey())));
              });
      return searchResponse.page().endCursor();
    }
  }

  private String findOrphans(final Consumer<SearchRequestPage> page) {
    // search for process instances that have a parent, are completed or canceled and already older
    // than expected
    final SearchResponse<ProcessInstance> searchResponse =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.parentProcessInstanceKey(l -> l.exists(true))
                        .state(
                            s ->
                                s.in(
                                    ProcessInstanceState.COMPLETED,
                                    ProcessInstanceState.TERMINATED))
                        .endDate(d -> d.lt(OffsetDateTime.now().minus(relaxedRetentionPolicy))))
            .sort(s -> s.endDate().asc())
            .page(page)
            .execute();

    if (searchResponse.items().isEmpty()) {
      return null;
    } else {
      searchResponse
          .items()
          .forEach(
              processInstance -> executor.execute(() -> treatPotentialOrphan(processInstance)));
      return searchResponse.page().endCursor();
    }
  }

  private void treatPotentialOrphan(final ProcessInstance processInstance) {
    camundaClient
        .newProcessInstanceGetRequest(processInstance.getParentProcessInstanceKey())
        .send()
        .exceptionallyAsync(
            t -> {
              LOG.info(
                  "Detected orphan process instance {}", processInstance.getProcessInstanceKey());
              treatOrphan(processInstance.getParentProcessInstanceKey(), p -> p.limit(100));
              return null;
            },
            executor);
  }

  private void treatOrphan(
      final long parentProcessInstanceKey, final Consumer<SearchRequestPage> page) {
    camundaClient
        .newProcessInstanceSearchRequest()
        .filter(f -> f.parentProcessInstanceKey(parentProcessInstanceKey))
        .page(page)
        .send()
        .thenAcceptAsync(
            r -> {
              if (!r.items().isEmpty()) {
                executor.execute(
                    () ->
                        treatOrphan(
                            parentProcessInstanceKey,
                            p -> p.limit(100).after(r.page().endCursor())));
                r.items()
                    .forEach(
                        processInstance -> {
                          executor.execute(
                              () ->
                                  treatOrphan(
                                      processInstance.getProcessInstanceKey(), p -> p.limit(100)));
                          executor.execute(
                              () ->
                                  deleteProcessInstance(
                                      String.valueOf(processInstance.getProcessInstanceKey())));
                        });
              }
            },
            executor);
  }

  private void deleteProcessInstance(final String processInstanceKey) {
    try {
      final ClassicHttpRequest r =
          new HttpDelete(
              new URIBuilder(camundaClient.getConfiguration().getRestAddress())
                  .appendPathSegments("v1", "process-instances", processInstanceKey)
                  .build());
      camundaClient.getConfiguration().getCredentialsProvider().applyCredentials(r::setHeader);
      httpClient.execute(r, new BasicHttpClientResponseHandler());
      LOG.info("Deleted process instance {}", processInstanceKey);
    } catch (final Exception e) {
      LOG.warn("Could not delete process instance {}", processInstanceKey, e);
    }
  }
}
