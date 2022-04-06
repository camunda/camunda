/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.util.it;

import static io.camunda.zeebe.exporter.util.it.EmbeddedBrokerRule.TEST_RECORD_EXPORTER_ID;
import static io.camunda.zeebe.test.util.record.RecordingExporter.processInstanceRecords;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.client.ClientProperties;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.TestUtil;
import io.camunda.zeebe.test.util.WorkloadGenerator;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.netty.util.NetUtil;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * JUnit test rule to facilitate running integration tests for exporters.
 *
 * <p>Sets up an embedded broker, gateway, client, and provides convenience methods to:
 *
 * <ol>
 *   <li>run a sample workload (e.g. deploy & create processes, setup a job worker, etc.)
 *   <li>visit all exported records so far
 *   <li>perform simple operations such as deployments, starting a job worker, etc.
 * </ol>
 *
 * The embedded broker is automatically started iff at least one exporter is configured. If not
 * explicitly configured, it must be started explicitly using {@link
 * ExporterIntegrationRule#start()}.
 *
 * <p>The broker is configured either implicitly through "zeebe.test.cfg.yaml" (found in the
 * resource classpath) or explicitly through one of the configure methods.
 *
 * <p>An example integration test suite could look like:
 *
 * <pre>
 * public class MyExporterIT {
 *   private final MyExporterConfig config = new MyExporterConfig();
 *   @Rule public final ExporterIntegrationRule exporterIntegrationRule =
 *      new ExporterIntegrationRule().configure("myExporter", MyExporter.class, config);
 *
 *   @Test
 *   public void shouldExportRecords() {
 *     // when
 *     exporterIntegrationRule.performSampleWorkload();
 *
 *     // then
 *     exporterIntegrationRule.visitExportedRecords(r -> {
 *       // code to assert the record was exported to the external system
 *     });
 *   }
 * }
 * </pre>
 *
 * You can additionally use the rule without annotating it, so that you can configure your exporter
 * differently in different tests:
 *
 * <pre>
 * public class MyExporterIT {
 *   private final ExporterIntegrationRule exporterIntegrationRule = new ExporterIntegrationRule();
 *
 *   @After
 *   public void tearDown() {
 *     exporterIntegrationRule.stop();
 *   }
 *
 *   @Test
 *   public void shouldExportRecords() {
 *     // given
 *     private final MyExporterConfig config = new MyExporterConfig();
 *     exporterIntegrationRule
 *       .configure("myExporter", MyExporter.class, config)
 *       .start();
 *
 *     // when
 *     exporterIntegrationRule.performSampleWorkload();
 *
 *     // then
 *     exporterIntegrationRule.visitExportedRecords(r -> {
 *       // code to assert the record was exported to the external system
 *     });
 *   }
 * }
 * </pre>
 *
 * NOTE: calls to the various configure methods are additive, so it is possible to configure more
 * than one exporter, as long as the IDs are different.
 */
public class ExporterIntegrationRule extends ExternalResource {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  private final RecordingExporterTestWatcher testWatcher = new RecordingExporterTestWatcher();
  private ClientRule clientRule;

  @Override
  public Statement apply(final Statement base, final Description description) {
    final var statement = testWatcher.apply(base, description);
    return super.apply(statement, description);
  }

  @Override
  protected void before() throws Throwable {
    super.before();

    if (!hasConfiguredExporters()) {
      start();
    }
  }

  @Override
  protected void after() {
    super.after();
    stop();
  }

  /**
   * Returns the current broker configuration.
   *
   * @return current broker configuration
   */
  public BrokerCfg getBrokerConfig() {
    return brokerRule.getBrokerCfg();
  }

  /**
   * @return the currently configured exporters
   */
  public List<ExporterCfg> getConfiguredExporters() {
    return getBrokerConfig().getExporters().entrySet().stream()
        .filter(entry -> !entry.getKey().equals(TEST_RECORD_EXPORTER_ID))
        .map(Entry::getValue)
        .collect(Collectors.toList());
  }

  /**
   * @return true if any exporter has been configured for the broker, false otherwise
   */
  public boolean hasConfiguredExporters() {
    return getConfiguredExporters().isEmpty();
  }

  /**
   * Configures the broker to use the given exporter.
   *
   * @param id the exporter ID
   * @param exporterClass the exporter class
   * @param configuration the configuration to use
   * @param <T> type of the configuration
   * @param <E> type of the exporter
   */
  public <T, E extends Exporter> ExporterIntegrationRule configure(
      final String id, final Class<E> exporterClass, final T configuration) {
    final Map<String, Object> arguments = convertConfigToMap(configuration);
    return configure(id, exporterClass, arguments);
  }

  /**
   * Configures the broker to use the given exporter.
   *
   * @param id the exporter ID
   * @param exporterClass the exporter class
   * @param arguments the arguments to pass during configuration
   * @param <E> type of the exporter
   */
  public <E extends Exporter> ExporterIntegrationRule configure(
      final String id, final Class<E> exporterClass, final Map<String, Object> arguments) {
    final ExporterCfg config = new ExporterCfg();
    config.setClassName(exporterClass.getCanonicalName());
    config.setArgs(arguments);

    return configure(Collections.singletonMap(id, config));
  }

  /**
   * Starts the broker and the client. This is blocking and will return once the broker is ready to
   * accept commands.
   *
   * @throws IllegalStateException if no exporter has previously been configured
   */
  public void start() {
    if (hasConfiguredExporters()) {
      throw new IllegalStateException("No exporter configured!");
    }

    RecordingExporter.setMaximumWaitTime(Duration.ofSeconds(10).toMillis());
    brokerRule.startBroker();
    clientRule = new ClientRule(this::newClientProperties);
    clientRule.createClient();
  }

  /** Stops the broker and destroys the client. Does nothing if not started yet. */
  public void stop() {
    brokerRule.stopBroker();

    if (clientRule != null) {
      clientRule.destroyClient();
    }
  }

  /** Runs a sample workload on the broker, exporting several records of different types. */
  public void performSampleWorkload() {
    WorkloadGenerator.performSampleWorkload(clientRule.getClient());
  }

  /**
   * Visits all exported records in the order they were exported.
   *
   * @param visitor record consumer
   */
  public void visitExportedRecords(final Consumer<Record<?>> visitor) {
    RecordingExporter.getRecords().forEach(visitor);
  }

  /**
   * Deploys the given process to the broker. Note that the filename must have the "bpmn" file
   * extension, e.g. "resource.bpmn".
   *
   * @param process process to deploy
   * @param filename resource name, e.g. "process.bpmn"
   */
  public void deployProcess(final BpmnModelInstance process, final String filename) {
    clientRule
        .getClient()
        .newDeployResourceCommand()
        .addProcessModel(process, filename)
        .send()
        .join();
  }

  /**
   * Deploys the given classpath resource to the broker.
   *
   * @param classpathResource the resource to deploy
   */
  public void deployResourceFromClasspath(final String classpathResource) {
    clientRule
        .getClient()
        .newDeployResourceCommand()
        .addResourceFromClasspath(classpathResource)
        .send()
        .join();
  }

  /**
   * Creates a process instance for the given process ID, with the given variables.
   *
   * @param processId BPMN process ID
   * @param variables initial variables for the instance
   * @return unique ID used to interact with the instance
   */
  public long createProcessInstance(final String processId, final Map<String, Object> variables) {
    return clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(variables)
        .send()
        .join()
        .getProcessInstanceKey();
  }

  /**
   * Creates a new job worker that will handle jobs of type {@param type}.
   *
   * <p>Make sure to close the returned job worker.
   *
   * @param type type of the jobs to handle
   * @param handler handler
   * @return a new JobWorker
   */
  public JobWorker createJobWorker(final String type, final JobHandler handler) {
    return clientRule.getClient().newWorker().jobType(type).handler(handler).open();
  }

  /**
   * Publishes a new message to the broker.
   *
   * @param messageName name of the message
   * @param correlationKey correlation key
   */
  public void publishMessage(final String messageName, final String correlationKey) {
    clientRule
        .getClient()
        .newPublishMessageCommand()
        .messageName(messageName)
        .correlationKey(correlationKey)
        .send()
        .join();
  }

  /**
   * Blocks and wait until the process identified by the key has been completed.
   *
   * @param processInstanceKey ID of the process
   */
  public void awaitProcessCompletion(final long processInstanceKey) {
    TestUtil.waitUntil(
        () ->
            processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .filter(r -> r.getKey() == processInstanceKey)
                .exists());
  }

  private Properties newClientProperties() {
    final Properties properties = new Properties();
    properties.put(
        ClientProperties.GATEWAY_ADDRESS,
        NetUtil.toSocketAddressString(
            getBrokerConfig().getGateway().getNetwork().toSocketAddress()));
    properties.put(ClientProperties.USE_PLAINTEXT_CONNECTION, "true");

    return properties;
  }

  private ExporterIntegrationRule configure(final Map<String, ExporterCfg> exporters) {
    getBrokerConfig().getExporters().putAll(exporters);

    return this;
  }

  private <T> Map<String, Object> convertConfigToMap(final T configuration) {
    return OBJECT_MAPPER.convertValue(configuration, new TypeReference<>() {});
  }
}
