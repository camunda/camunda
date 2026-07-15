/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.data;

import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static io.camunda.webapps.schema.entities.AbstractExporterEntity.DEFAULT_TENANT_ID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.impl.command.StreamUtil;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.operate.data.usertest.UserTestDataGenerator;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.auth.SecurityContext;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCancelRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.ResourceServices.DeployResourcesRequest;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;

@DependsOn("searchEngineSchemaInitializer")
public abstract class AbstractDataGenerator implements DataGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDataGenerator.class);

  /**
   * Substrings of the transient ES/OS errors seen while the Operate schema is still being created
   * by the async schema initializer.
   */
  private static final List<String> SCHEMA_NOT_READY_MARKERS =
      List.of("index_not_found_exception", "no_shard_available_action_exception");

  private static final ObjectMapper VARIABLES_MAPPER = new ObjectMapper();

  @Autowired
  @Qualifier("camundaClient")
  protected CamundaClient client;

  protected boolean manuallyCalled = false;
  protected ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

  @Autowired ServiceRegistry serviceRegistry;

  @Autowired CamundaAuthenticationProvider authenticationProvider;

  @Autowired private CamundaSecurityLibraryProperties cslProperties;
  private boolean shutdown = false;

  @Autowired(required = false)
  private SearchClientsProxy searchClientsProxy;

  @PostConstruct
  private void startDataGenerator() {
    startGeneratingData();
  }

  protected void startGeneratingData() {
    LOGGER.debug("INIT: Generate demo data...");
    try {
      createZeebeDataAsync(false);
    } catch (final Exception ex) {
      LOGGER.debug("Demo data could not be generated. Cause: {}", ex.getMessage());
      LOGGER.error("Error occurred when generating demo data.", ex);
    }
  }

  @PreDestroy
  public void shutdown() {
    LOGGER.info("Shutdown DataGenerator");
    shutdown = true;
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(200, TimeUnit.MILLISECONDS)) {
          scheduler.shutdownNow();
        }
      } catch (final InterruptedException e) {
        scheduler.shutdownNow();
      }
    }
  }

  @Override
  public void createZeebeDataAsync(final boolean manuallyCalled) {
    scheduler.execute(
        () -> {
          Boolean zeebeDataCreated = null;
          while (zeebeDataCreated == null && !shutdown) {
            try {
              zeebeDataCreated = createZeebeData(manuallyCalled);
            } catch (final Exception ex) {
              if (isSchemaNotReady(ex)) {
                LOGGER.debug("Operate schema is not ready yet, will retry: {}", ex.getMessage());
              } else {
                LOGGER.error(
                    String.format(
                        "Error occurred when creating demo data: %s. Retrying...", ex.getMessage()),
                    ex);
              }
              sleepFor(2000);
            }
          }
        });
  }

  public boolean createZeebeData(final boolean manuallyCalled) {
    this.manuallyCalled = manuallyCalled;

    if (!shouldCreateData(manuallyCalled)) {
      return false;
    }

    return true;
  }

  public boolean shouldCreateData(final boolean manuallyCalled) {
    if (searchClientsProxy == null) {
      LOGGER.warn(
          "SearchClientsProxy is null. Assuming no data exists and it should be created...");
      return true;
    }
    if (!manuallyCalled) { // when called manually, always create the data
      final boolean exists;
      exists =
          searchClientsProxy
                  .withPhysicalTenant(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                  .withSecurityContext(
                      SecurityContext.of(b -> b.withAuthentication(a -> a.anonymous(true))))
                  .searchProcessDefinitions(ProcessDefinitionQuery.of(q -> q))
                  .total()
              > 0;
      if (exists) {
        // data already exists
        LOGGER.debug("Data already exists in Zeebe.");
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings("checkstyle:MissingSwitchDefault")
  protected JobWorker progressSimpleTask(final String taskType) {
    return client
        .newWorker()
        .jobType(taskType)
        .handler(
            (jobClient, job) -> {
              final int scenarioCount = ThreadLocalRandom.current().nextInt(3);
              switch (scenarioCount) {
                case 0:
                  // timeout
                  break;
                case 1:
                  // successfully complete task
                  jobClient.newCompleteCommand(job.getKey()).send().join();
                  break;
                case 2:
                  // fail task -> create incident
                  jobClient.newFailCommand(job.getKey()).retries(0).send().join();
                  break;
              }
            })
        .name("operate")
        .timeout(Duration.ofSeconds(UserTestDataGenerator.JOB_WORKER_TIMEOUT))
        .open();
  }

  protected JobWorker progressSimpleTask(final String taskType, final int retriesLeft) {
    return client
        .newWorker()
        .jobType(taskType)
        .handler(
            (jobClient, job) ->
                jobClient.newFailCommand(job.getKey()).retries(retriesLeft).send().join())
        .name("operate")
        .timeout(Duration.ofSeconds(UserTestDataGenerator.JOB_WORKER_TIMEOUT))
        .open();
  }

  protected String getTenant(final String tenantId) {
    if (cslProperties.getMultiTenancy().isChecksEnabled()) {
      return tenantId;
    }
    return DEFAULT_TENANT_ID;
  }

  boolean isSchemaNotReady(final Throwable ex) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      final String message = cause.getMessage();
      if (message != null && SCHEMA_NOT_READY_MARKERS.stream().anyMatch(message::contains)) {
        return true;
      }
    }
    return false;
  }

  protected Long deployProcess(
      final boolean ignoreException, final String tenantId, final String... classpathResources) {
    try {
      if (classpathResources.length == 0) {
        return null;
      }
      final DeploymentRecord deploymentRecord =
          deployResourcesAnonymously(tenantId, classpathResources);
      LOGGER.debug("Deployment of resource [{}] was performed", (Object[]) classpathResources);
      final List<ProcessMetadataValue> processes = deploymentRecord.getProcessesMetadata();
      return processes.get(processes.size() - 1).getProcessDefinitionKey();
    } catch (final Exception e) {
      if (ignoreException) {
        LOGGER.warn("Deployment failed: " + e.getMessage());
        return null;
      } else {
        throw e;
      }
    }
  }

  protected void deployDecision(final String tenantId, final String... classpathResources) {
    if (classpathResources.length == 0) {
      return;
    }
    deployResourcesAnonymously(tenantId, classpathResources);
    LOGGER.debug("Deployment of resource [{}] was performed", (Object[]) classpathResources);
  }

  private DeploymentRecord deployResourcesAnonymously(
      final String tenantId, final String... classpathResources) {
    try {
      final Map<String, byte[]> resources = new LinkedHashMap<>();
      for (final String classpathResource : classpathResources) {
        resources.put(classpathResource, readClasspathResource(classpathResource));
      }
      return executeCamundaServiceAnonymously(
          authentication ->
              serviceRegistry
                  .resourceServices(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                  .deployResources(
                      new DeployResourcesRequest(resources, tenantId), authentication));
    } catch (final IOException e) {
      throw new OperateRuntimeException(
          "Cannot deploy resource from classpath. " + e.getMessage(), e);
    }
  }

  private byte[] readClasspathResource(final String classpathResource) throws IOException {
    try (final InputStream resourceStream =
        getClass().getClassLoader().getResourceAsStream(classpathResource)) {
      if (resourceStream == null) {
        throw new FileNotFoundException(classpathResource);
      }
      return StreamUtil.readInputStream(resourceStream);
    }
  }

  protected long startProcessInstance(
      final boolean ignoreException,
      final String tenantId,
      final String bpmnProcessId,
      final String payload) {
    try {
      final Map<String, Object> variables = parseVariables(payload);
      ProcessInstanceCreationRecord record;
      try {
        record = createProcessInstanceAnonymously(bpmnProcessId, tenantId, variables);
      } catch (final Exception ex) {
        // retry once
        sleepFor(300L);
        record = createProcessInstanceAnonymously(bpmnProcessId, tenantId, variables);
      }
      LOGGER.debug("Process instance created for process [{}]", bpmnProcessId);
      return record.getProcessInstanceKey();
    } catch (final Exception e) {
      if (ignoreException) {
        LOGGER.warn("Instance creation failed: " + e.getMessage());
        return 0L;
      } else {
        throw e;
      }
    }
  }

  private ProcessInstanceCreationRecord createProcessInstanceAnonymously(
      final String bpmnProcessId, final String tenantId, final Map<String, Object> variables) {
    return executeCamundaServiceAnonymously(
        authentication ->
            serviceRegistry
                .processInstanceServices(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .createProcessInstance(
                    new ProcessInstanceCreateRequest(
                        -1L,
                        bpmnProcessId,
                        -1,
                        variables,
                        tenantId,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        Set.of(),
                        null),
                    authentication));
  }

  private Map<String, Object> parseVariables(final String payload) {
    if (payload == null || payload.isEmpty()) {
      return Map.of();
    }
    try {
      return VARIABLES_MAPPER.readValue(payload, new TypeReference<Map<String, Object>>() {});
    } catch (final IOException e) {
      throw new OperateRuntimeException("Cannot parse process instance variables payload", e);
    }
  }

  protected void cancelProcessInstance(
      final boolean ignoreException, final long processInstanceKey) {
    try {
      executeCamundaServiceAnonymously(
          authentication ->
              serviceRegistry
                  .processInstanceServices(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                  .cancelProcessInstance(
                      new ProcessInstanceCancelRequest(processInstanceKey, null), authentication));
    } catch (final Exception e) {
      if (!ignoreException) {
        throw e;
      } else {
        LOGGER.warn("Cancellation failed: " + e.getMessage());
      }
    }
  }

  protected void resolveIncidentForProcessInstance(final long processInstanceKey) {
    executeCamundaServiceAnonymously(
        authentication ->
            serviceRegistry
                .processInstanceServices(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .resolveProcessInstanceIncidents(processInstanceKey, authentication));
  }

  private <T> T executeCamundaServiceAnonymously(
      final Function<CamundaAuthentication, CompletableFuture<T>> method) {
    return method.apply(authenticationProvider.getAnonymousCamundaAuthentication()).join();
  }
}
