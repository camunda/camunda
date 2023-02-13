/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.it.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.ApplicationContextProvider;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.rest.engine.PlatformEngineContextFactory;
import org.camunda.optimize.service.KpiEvaluationSchedulerService;
import org.camunda.optimize.service.KpiService;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.SettingsService;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.archive.ProcessInstanceArchivingService;
import org.camunda.optimize.service.cleanup.CleanupScheduler;
import org.camunda.optimize.service.dashboard.InstantPreviewDashboardService;
import org.camunda.optimize.service.dashboard.ManagementDashboardService;
import org.camunda.optimize.service.digest.DigestService;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.writer.AbstractProcessInstanceDataWriter;
import org.camunda.optimize.service.es.writer.InstantDashboardMetadataWriter;
import org.camunda.optimize.service.es.writer.activity.RunningActivityInstanceWriter;
import org.camunda.optimize.service.events.ExternalEventService;
import org.camunda.optimize.service.events.rollover.EventIndexRolloverService;
import org.camunda.optimize.service.events.rollover.ExternalProcessVariableIndexRolloverService;
import org.camunda.optimize.service.identity.PlatformIdentityService;
import org.camunda.optimize.service.identity.PlatformUserIdentityCache;
import org.camunda.optimize.service.identity.PlatformUserTaskIdentityCache;
import org.camunda.optimize.service.importing.AbstractImportScheduler;
import org.camunda.optimize.service.importing.EngineImportIndexHandler;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.ImportSchedulerManagerService;
import org.camunda.optimize.service.importing.PositionBasedImportIndexHandler;
import org.camunda.optimize.service.importing.engine.EngineImportScheduler;
import org.camunda.optimize.service.importing.engine.mediator.DefinitionXmlImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.StoreEngineImportProgressMediator;
import org.camunda.optimize.service.importing.engine.mediator.factory.CamundaEventImportServiceFactory;
import org.camunda.optimize.service.importing.engine.service.ImportObserver;
import org.camunda.optimize.service.importing.engine.service.RunningActivityInstanceImportService;
import org.camunda.optimize.service.importing.engine.service.definition.DecisionDefinitionResolverService;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.importing.event.EventTraceStateProcessingScheduler;
import org.camunda.optimize.service.importing.eventprocess.EventBasedProcessesInstanceImportScheduler;
import org.camunda.optimize.service.importing.eventprocess.EventProcessInstanceImportMediatorManager;
import org.camunda.optimize.service.importing.ingested.IngestedDataImportScheduler;
import org.camunda.optimize.service.importing.ingested.mediator.StoreIngestedImportProgressMediator;
import org.camunda.optimize.service.importing.page.TimestampBasedImportPage;
import org.camunda.optimize.service.importing.zeebe.mediator.StorePositionBasedImportProgressMediator;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.telemetry.TelemetryScheduler;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;

@Slf4j
public class EmbeddedOptimizeExtension
  implements BeforeEachCallback, AfterEachCallback, BeforeAllCallback, AfterAllCallback {

  public static final String DEFAULT_ENGINE_ALIAS = "camunda-bpm";

  private final boolean beforeAllMode;
  private ApplicationContext applicationContext;

  private OptimizeRequestExecutor requestExecutor;
  private ObjectMapper objectMapper;
  private boolean resetImportOnStart = true;
  @Getter
  @Setter
  private boolean closeContextAfterTest = false;

  private static final ObjectMapper configObjectMapper = new ObjectMapper().registerModules(
    new JavaTimeModule(), new Jdk8Module()
  );
  private static String serializedDefaultConfiguration;

  public EmbeddedOptimizeExtension() {
    this(false);
  }

  public EmbeddedOptimizeExtension(final boolean beforeAllMode) {
    this.beforeAllMode = beforeAllMode;
  }

  @SneakyThrows
  @Override
  public void beforeAll(final ExtensionContext extensionContext) {
    setApplicationContext(SpringExtension.getApplicationContext(extensionContext));

    if (serializedDefaultConfiguration == null) {
      // store the default configuration to restore it later
      serializedDefaultConfiguration = configObjectMapper.writeValueAsString(getConfigurationService());
    }
    if (beforeAllMode) {
      setupOptimize();
    }
  }

  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
    applicationContext.getBean(ApplicationContextProvider.class).setApplicationContext(applicationContext);
  }

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    setApplicationContext(SpringExtension.getApplicationContext(extensionContext));
    initMetadataIfMissing();
    if (!beforeAllMode) {
      setupOptimize();
    }
  }

  @Override
  public void afterAll(final ExtensionContext extensionContext) {
    if (beforeAllMode) {
      afterTest();
    }
  }

  @Override
  public void afterEach(final ExtensionContext extensionContext) {
    if (!beforeAllMode) {
      afterTest();
    }
    if (closeContextAfterTest) {
      ((ConfigurableApplicationContext) applicationContext).close();
      setCloseContextAfterTest(false);
      setApplicationContext(SpringExtension.getApplicationContext(extensionContext));
      reloadConfiguration();
    }
  }

  public void setupOptimize() {
    try {
      objectMapper = getBean(ObjectMapper.class);
      requestExecutor =
        new OptimizeRequestExecutor(
          DEFAULT_USERNAME, DEFAULT_PASSWORD, IntegrationTestConfigurationUtil.getEmbeddedOptimizeRestApiEndpoint(applicationContext)
        ).initAuthCookie();
      if (isResetImportOnStart()) {
        resetImportStartIndexes();
        setResetImportOnStart(true);
      }
    } catch (Exception e) {
      final String message = "Failed starting embedded Optimize.";
      log.error(message, e);
      throw new OptimizeIntegrationTestException(message, e);
    }
  }

  public void afterTest() {
    try {
      this.getAlertService().getScheduler().clear();
      stopImportScheduling();
      resetConfiguration();
      LocalDateUtil.reset();
      reloadConfiguration();
    } catch (Exception e) {
      log.error("Failed to clean up after test", e);
    }
  }

  public void configureEsHostAndPort(final String host, final int esPort) {
    getConfigurationService().getElasticsearchConnectionNodes().get(0).setHost(host);
    getConfigurationService().getElasticsearchConnectionNodes().get(0).setHttpPort(esPort);
    reloadConfiguration();
  }

  public void configureEngineRestEndpointForEngineWithName(final String engineName, final String restEndpoint) {
    getConfigurationService()
      .getConfiguredEngines()
      .values()
      .stream()
      .filter(config -> config.getName().equals(engineName))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("Cannot find configured engine with name " + engineName))
      .setRest(restEndpoint);
  }

  public void startContinuousImportScheduling() {
    getImportSchedulerManager().startSchedulers();
  }

  public void stopImportScheduling() {
    getImportSchedulerManager().stopSchedulers();
  }

  @SneakyThrows
  public void importAllEngineData() {
    boolean isDoneImporting;
    do {
      isDoneImporting = true;
      for (EngineImportScheduler scheduler : getImportSchedulerManager().getEngineImportSchedulers()) {
        scheduler.runImportRound(false);
        isDoneImporting &= !scheduler.isImporting();
      }
    } while (!isDoneImporting);
  }

  public void importAllEngineEntitiesFromScratch() {
    try {
      resetImportStartIndexes();
    } catch (Exception e) {
      // nothing to do
    }
    importAllEngineEntitiesFromLastIndex();
  }

  public void importAllEngineEntitiesFromLastIndex() {
    for (EngineImportScheduler scheduler : getImportSchedulerManager().getEngineImportSchedulers()) {
      log.debug("scheduling import round");
      scheduleImportAndWaitUntilIsFinished(scheduler);
    }
  }

  @SneakyThrows
  public void importIngestedDataFromScratch() {
    try {
      resetImportStartIndexes();
    } catch (Exception e) {
      // nothing to do
    }
    final IngestedDataImportScheduler scheduler = getImportSchedulerManager().getIngestedDataImportScheduler()
      .orElseThrow();
    scheduler.runImportRound(true).get();
  }

  @SneakyThrows
  public void importIngestedDataFromLastIndex() {
    final IngestedDataImportScheduler scheduler = getImportSchedulerManager().getIngestedDataImportScheduler()
      .orElseThrow();
    scheduler.runImportRound(true).get();
  }

  @SneakyThrows
  public void importAllZeebeEntitiesFromScratch() {
    try {
      resetImportStartIndexes();
    } catch (Exception e) {
      // nothing to do
    }
    importAllZeebeEntitiesFromLastIndex();
  }

  @SneakyThrows
  public void importAllZeebeEntitiesFromLastIndex() {
    getImportSchedulerManager().getZeebeImportScheduler()
      .orElseThrow(() -> new OptimizeIntegrationTestException("No Zeebe Scheduler present"))
      .runImportRound(true).get();
  }

  @SneakyThrows
  public void importRunningActivityInstance(List<HistoricActivityInstanceEngineDto> activities) {
    RunningActivityInstanceWriter writer = getBean(RunningActivityInstanceWriter.class);
    ProcessDefinitionResolverService processDefinitionResolverService =
      getBean(ProcessDefinitionResolverService.class);
    CamundaEventImportServiceFactory camundaEventServiceFactory =
      getBean(CamundaEventImportServiceFactory.class);

    for (EngineContext configuredEngine : getConfiguredEngines()) {
      final RunningActivityInstanceImportService service =
        new RunningActivityInstanceImportService(
          writer,
          camundaEventServiceFactory.createCamundaEventService(configuredEngine),
          configuredEngine,
          getConfigurationService(),
          processDefinitionResolverService
        );
      try {
        CompletableFuture<Void> done = new CompletableFuture<>();
        service.executeImport(activities, () -> done.complete(null));
        done.get();
      } finally {
        service.shutdown();
      }
    }
  }

  @SneakyThrows
  public Optional<DecisionDefinitionOptimizeDto> getDecisionDefinitionFromResolverService(final String definitionId) {
    DecisionDefinitionResolverService resolverService =
      getBean(DecisionDefinitionResolverService.class);
    for (EngineContext configuredEngine : getConfiguredEngines()) {
      final Optional<DecisionDefinitionOptimizeDto> definition =
        resolverService.getDefinition(definitionId, configuredEngine);
      if (definition.isPresent()) {
        return definition;
      }
    }
    return Optional.empty();
  }

  @SneakyThrows
  public Optional<ProcessDefinitionOptimizeDto> getProcessDefinitionFromResolverService(final String definitionId) {
    ProcessDefinitionResolverService resolverService =
      getBean(ProcessDefinitionResolverService.class);
    for (EngineContext configuredEngine : getConfiguredEngines()) {
      final Optional<ProcessDefinitionOptimizeDto> definition =
        resolverService.getDefinition(definitionId, configuredEngine);
      if (definition.isPresent()) {
        return definition;
      }
    }
    return Optional.empty();
  }

  @SneakyThrows
  private void scheduleImportAndWaitUntilIsFinished(EngineImportScheduler scheduler) {
    scheduler.runImportRound(true).get();
    // as the definition is imported in two steps,
    // we need to run the xml imports once more as they depend on the definition entry to be present in elastic
    // which is not guaranteed from the import round, as the write request of the definitions may not have
    // been persisted when the xml importers were run
    runDefinitionXmlImporterMediators(scheduler);
  }

  @SneakyThrows
  private void runDefinitionXmlImporterMediators(EngineImportScheduler scheduler) {
    final List<ImportMediator> definitionXmlMediators = scheduler.getImportMediators()
      .stream()
      .filter(mediator -> mediator instanceof DefinitionXmlImportMediator)
      .collect(Collectors.toList());
    scheduler.executeImportRound(definitionXmlMediators).get();
  }

  public void storeImportIndexesToElasticsearch() {
    final List<CompletableFuture<Void>> synchronizationCompletables = new ArrayList<>();
    final List<AbstractImportScheduler<?>> importSchedulers = getImportSchedulerManager().getImportSchedulers()
      .stream().filter(scheduler -> getConfigurationService().isImportEnabled(scheduler.getDataImportSourceDto()))
      .collect(Collectors.toList());
    for (AbstractImportScheduler<?> scheduler : importSchedulers) {
      synchronizationCompletables.addAll(
        scheduler.getImportMediators()
          .stream()
          .filter(med -> med instanceof StoreEngineImportProgressMediator
            || med instanceof StoreIngestedImportProgressMediator
            || med instanceof StorePositionBasedImportProgressMediator)
          .map(mediator -> {
            mediator.resetBackoff();
            return mediator.runImport();
          })
          .collect(Collectors.toList())
      );
    }
    CompletableFuture.allOf(synchronizationCompletables.toArray(new CompletableFuture[0])).join();
  }

  private Collection<EngineContext> getConfiguredEngines() {
    return getBean(PlatformEngineContextFactory.class).getConfiguredEngines();
  }

  public EngineConfiguration getDefaultEngineConfiguration() {
    return getConfigurationService()
      .getEngineConfiguration(DEFAULT_ENGINE_ALIAS)
      .orElseThrow(() -> new OptimizeIntegrationTestException("Missing default engine configuration"));
  }

  @SneakyThrows
  public void ensureImportSchedulerIsIdle(long timeoutSeconds) {
    final CountDownLatch importIdleLatch = new CountDownLatch(
      getImportSchedulerManager().getEngineImportSchedulers().size());
    for (EngineImportScheduler scheduler : getImportSchedulerManager().getEngineImportSchedulers()) {
      if (scheduler.isImporting()) {
        log.info("Scheduler is still importing, waiting for it to finish.");
        final ImportObserver importObserver = new ImportObserver() {
          @Override
          public void importInProgress(final String engineAlias) {
            // noop
          }

          @Override
          public void importIsIdle(final String engineAlias) {
            log.info("Scheduler became idle, counting down latch.");
            importIdleLatch.countDown();
            scheduler.unsubscribe(this);
          }
        };
        scheduler.subscribe(importObserver);

      } else {
        log.info("Scheduler is not importing, counting down latch.");
        importIdleLatch.countDown();
      }
    }

    try {
      importIdleLatch.await(timeoutSeconds, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public ImportSchedulerManagerService getImportSchedulerManager() {
    return getBean(ImportSchedulerManagerService.class);
  }

  public OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutor;
  }

  public String getNewAuthenticationToken() {
    return authenticateUser(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public String authenticateUser(String username, String password) {
    Response tokenResponse = authenticateUserRequest(username, password);
    return tokenResponse.readEntity(String.class);
  }

  public Response authenticateUserRequest(String username, String password) {
    final CredentialsRequestDto entity = new CredentialsRequestDto(username, password);
    return target("authentication")
      .request()
      .post(Entity.json(entity));
  }

  public void initMetadataIfMissing() {
    getElasticsearchMetadataService().initMetadataIfMissing(getOptimizeElasticClient());
  }

  public void reloadConfiguration() {
    // reset engine context factory first to ensure we have new clients before reinitializing any other object
    // as they might make use of the engine client
    final EngineContextFactory engineContextFactory =
      getBean(PlatformEngineContextFactory.class);
    engineContextFactory.close();
    engineContextFactory.init();

    final Map<String, ?> refreshableServices = getApplicationContext().getBeansOfType(ConfigurationReloadable.class);
    for (Map.Entry<String, ?> entry : refreshableServices.entrySet()) {
      Object beanRef = entry.getValue();
      if (beanRef instanceof ConfigurationReloadable) {
        ConfigurationReloadable reloadable = (ConfigurationReloadable) beanRef;
        reloadable.reloadConfiguration(getApplicationContext());
      }
    }


    // warmup the elastic client with default options (to not make use of plugins)
    // this is done to fully initialize the client as the client does a version validation on the first request
    try {
      getOptimizeElasticClient().getHighLevelClient().info(RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Could not get cluster info from Elasticsearch", e);
    }
  }

  public void resetConfiguration() throws IOException {
    log.info("resetting config, parsing defaultconfig and copying properties");
    // copy all properties from the default configuration to the embedded optimize
    BeanUtils.copyProperties(
      configObjectMapper.readValue(serializedDefaultConfiguration, ConfigurationService.class),
      getBean(ConfigurationService.class)
    );
    log.info("done resetting config");
  }

  public void reloadTenantCache() {
    getTenantService().reloadConfiguration(null);
  }

  public final WebTarget target(String path) {
    return requestExecutor.getDefaultWebTarget().path(path);
  }

  public final WebTarget target() {
    return requestExecutor.getDefaultWebTarget();
  }

  public final WebTarget rootTarget(String path) {
    return requestExecutor.createWebTarget(IntegrationTestConfigurationUtil.getEmbeddedOptimizeEndpoint(applicationContext))
      .path(path);
  }

  public final WebTarget securedRootTarget() {
    return requestExecutor.createWebTarget(IntegrationTestConfigurationUtil.getSecuredEmbeddedOptimizeEndpoint(applicationContext));
  }

  public List<Long> getImportIndexes() {
    List<Long> indexes = new LinkedList<>();
    for (String engineAlias : getConfigurationService().getConfiguredEngines().keySet()) {
      getIndexHandlerRegistry()
        .getAllEntitiesBasedHandlers(engineAlias)
        .forEach(handler -> indexes.add(handler.getImportIndex()));
      getIndexHandlerRegistry()
        .getTimestampEngineBasedHandlers(engineAlias)
        .forEach(handler -> {
          TimestampBasedImportPage page = handler.getNextPage();
          indexes.add(page.getTimestampOfLastEntity().toEpochSecond());
        });
    }
    return indexes;
  }

  public void resetImportStartIndexes() {
    for (EngineImportIndexHandler<?, ?> engineImportIndexHandler :
      getIndexHandlerRegistry().getAllEngineImportHandlers()) {
      engineImportIndexHandler.resetImportIndex();
    }
    getAllPositionBasedImportHandlers().forEach(PositionBasedImportIndexHandler::resetImportIndex);
  }

  public List<PositionBasedImportIndexHandler> getAllPositionBasedImportHandlers() {
    List<PositionBasedImportIndexHandler> positionBasedHandlers = new LinkedList<>();
    for (int partitionId = 1; partitionId <=
      getConfigurationService().getConfiguredZeebe().getPartitionCount(); partitionId++) {
      positionBasedHandlers.addAll(getIndexHandlerRegistry().getPositionBasedHandlers(partitionId));
    }
    return positionBasedHandlers;
  }

  public void resetInstanceDataWriters() {
    final Map<String, AbstractProcessInstanceDataWriter> writers =
      getApplicationContext().getBeansOfType(AbstractProcessInstanceDataWriter.class);
    for (AbstractProcessInstanceDataWriter<?> writer : writers.values()) {
      writer.reloadConfiguration(getApplicationContext());
    }
  }

  public void reinitializeSchema() {
    getElasticSearchSchemaManager().initializeSchema(getOptimizeElasticClient());
  }

  public ApplicationContext getApplicationContext() {
    return applicationContext;
  }

  public <T> T getBean(Class<T> clazz) {
    return applicationContext.getBean(clazz);
  }

  public DateTimeFormatter getDateTimeFormatter() {
    return getBean(DateTimeFormatter.class);
  }

  public ConfigurationService getConfigurationService() {
    return getBean(ConfigurationService.class);
  }

  public ManagementDashboardService getManagementDashboardService() {
    return getApplicationContext().getBean(ManagementDashboardService.class);
  }

  public InstantPreviewDashboardService getInstantPreviewDashboardService() {
    return getApplicationContext().getBean(InstantPreviewDashboardService.class);
  }

  public InstantDashboardMetadataWriter getInstantPreviewDashboardWriter() {
    return getApplicationContext().getBean(InstantDashboardMetadataWriter.class);
  }

  public CleanupScheduler getCleanupScheduler() {
    return getBean(CleanupScheduler.class);
  }

  public KpiEvaluationSchedulerService getKpiSchedulerService() {
    return getBean(KpiEvaluationSchedulerService.class);
  }

  public TelemetryScheduler getTelemetryScheduler() {
    return getBean(TelemetryScheduler.class);
  }

  public ProcessInstanceArchivingService getProcessInstanceArchivingService() {
    return getBean(ProcessInstanceArchivingService.class);
  }

  public PlatformUserIdentityCache getUserIdentityCache() {
    return getBean(PlatformUserIdentityCache.class);
  }

  public PlatformUserTaskIdentityCache getUserTaskIdentityCache() {
    return getBean(PlatformUserTaskIdentityCache.class);
  }

  public EventIndexRolloverService getEventIndexRolloverService() {
    return getBean(EventIndexRolloverService.class);
  }

  public ExternalProcessVariableIndexRolloverService getExternalProcessVariableIndexRolloverService() {
    return getBean(ExternalProcessVariableIndexRolloverService.class);
  }

  public LocalizationService getLocalizationService() {
    return getBean(LocalizationService.class);
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public ImportIndexHandlerRegistry getIndexHandlerRegistry() {
    return getBean(ImportIndexHandlerRegistry.class);
  }

  public AlertService getAlertService() {
    return getBean(AlertService.class);
  }

  public DigestService getDigestService() {
    return getBean(DigestService.class);
  }

  public TenantService getTenantService() {
    return getBean(TenantService.class);
  }

  public KpiService getKpiService() {
    return getApplicationContext().getBean(KpiService.class);
  }

  public PlatformIdentityService getIdentityService() {
    return getBean(PlatformIdentityService.class);
  }

  @SneakyThrows
  public void processEvents() {
    EventTraceStateProcessingScheduler eventProcessingScheduler = getEventProcessingScheduler();

    // run one cycle
    eventProcessingScheduler.runImportRound(true).get();

    // do final progress update
    eventProcessingScheduler.getEventProcessingProgressMediator().runImport().get();
  }

  public ExternalEventService getEventService() {
    return getBean(ExternalEventService.class);
  }

  public SettingsService getSettingsService() {
    return getBean(SettingsService.class);
  }

  public OptimizeIndexNameService getIndexNameService() {
    return getBean(OptimizeIndexNameService.class);
  }

  public EventTraceStateProcessingScheduler getEventProcessingScheduler() {
    return getBean(EventTraceStateProcessingScheduler.class);
  }

  public EventProcessInstanceImportMediatorManager getEventProcessInstanceImportMediatorManager() {
    return getBean(EventProcessInstanceImportMediatorManager.class);
  }

  public EventBasedProcessesInstanceImportScheduler getEventBasedProcessesInstanceImportScheduler() {
    return getBean(EventBasedProcessesInstanceImportScheduler.class);
  }

  public ElasticSearchSchemaManager getElasticSearchSchemaManager() {
    return getBean(ElasticSearchSchemaManager.class);
  }

  public OptimizeElasticsearchClient getOptimizeElasticClient() {
    return getBean(OptimizeElasticsearchClient.class);
  }

  public ElasticsearchMetadataService getElasticsearchMetadataService() {
    return getBean(ElasticsearchMetadataService.class);
  }

  private boolean isResetImportOnStart() {
    return resetImportOnStart;
  }

  public void setResetImportOnStart(final boolean resetImportOnStart) {
    this.resetImportOnStart = resetImportOnStart;
  }

  public String format(OffsetDateTime offsetDateTime) {
    return this.getDateTimeFormatter().format(offsetDateTime);
  }

  public String formatToHistogramBucketKey(final OffsetDateTime offsetDateTime, final ChronoUnit unit) {
    return getDateTimeFormatter().format(truncateToStartOfUnit(offsetDateTime, unit));
  }

  @SneakyThrows
  public String toJsonString(final Object object) {
    return getObjectMapper().writeValueAsString(object);
  }

}
