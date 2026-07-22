/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * Verifies that per-physical-tenant exporter configuration is applied at the exporter level.
 *
 * <p>Supported today: an exporter declared in the <b>root</b> configuration can be overridden per
 * physical tenant under {@code camunda.physical-tenants.<tenantId>.data.exporters.<id>.*}. The root
 * entry's {@code className}/{@code jarPath} are always inherited (diverging from them is a boot
 * error). If the exporter's class ships an {@code ExporterConfigMerger} (the bundled ES/OS and
 * Camunda exporters do), a partial override is deep-merged over the root args; for any other class
 * the tenant's args replace the root args wholesale and must therefore be complete (ADR-0008 step
 * 1). Each tenant's partition group then runs the exporter with that tenant's resolved
 * configuration.
 *
 * <p>Not yet supported (both gated on <a
 * href="https://github.com/camunda/camunda/issues/56652">#56652</a>, see the disabled tests below):
 * declaring an exporter <b>only</b> for a physical tenant (absent from the root config), and
 * <b>narrowing</b> a root-declared exporter out of a tenant via its {@code exporters-assigned}
 * manifest. The initial cluster configuration — which decides which exporter ids are enabled on a
 * partition — is still generated from the root {@code BrokerCfg} only, so a tenant-only exporter is
 * never enabled, and a narrowed-away exporter would stay enabled (and turn into a {@code
 * BlockingExporter}). Until the cluster-configuration layer derives that state per tenant, the
 * assignment step stays dormant and the interim inherit-all behavior stands.
 */
@Timeout(120)
@ZeebeIntegration
final class PhysicalTenantExporterConfigIT {

  private static final String TENANT_A = "tenanta";
  private static final String LOCATION_EXPORTER_ID = "location-exporter";
  private static final String TARGET_ARG = "target";
  private static final String ROOT_TARGET = "root-location";
  private static final String TENANT_A_TARGET = "tenanta-location";

  private static final String TENANT_A_ONLY_EXPORTER_ID = "tenanta-exporter";

  private static final String ES_MERGE_EXPORTER_ID = "esmerge";
  private static final String ROOT_INDEX_PREFIX = "rootmergeprefix";
  private static final String TENANT_A_INDEX_PREFIX = "tenantamergeprefix";

  // A root-declared exporter that tenantA's exporters-assigned manifest deliberately omits, used by
  // the disabled narrowing test only.
  private static final String NARROWED_EXPORTER_ID = "narrowed-exporter";
  private static final String DEFAULT_NARROW_PROCESS = "default-narrow-process";
  private static final String TENANT_A_NARROW_PROCESS = "tenanta-narrow-process";

  @SuppressWarnings("resource")
  private static final ElasticsearchContainer ES =
      TestSearchContainers.createDefaultElasticsearchContainer();

  private static final String ES_URL;

  static {
    ES.start();
    ES_URL = "http://" + ES.getHttpHostAddress();
  }

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  // The exporters are declared in the ROOT config (so they are enabled on every partition group).
  // tenantA redeclares the location exporter fully with a different "target" arg (the
  // whole-entry-replace path for a class without a merger), and overrides ONLY the index prefix
  // of the Elasticsearch exporter (the deep-merge path: its class ships an ExporterConfigMerger,
  // so url and bulk tuning are inherited from the root entry). The tenantA-only exporter
  // exercises the not-yet-supported case and is used by the disabled test only. The narrowed
  // exporter is root-declared but omitted from tenantA's exporters-assigned manifest, so once
  // narrowing is wired in (#56652) it must stop running on tenantA's partition group.
  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS
          .configure(new TestStandaloneBroker().withUnauthenticatedAccess())
          .withExporter(
              LOCATION_EXPORTER_ID,
              cfg -> {
                cfg.setClassName(LocationRecordingExporter.class.getName());
                cfg.setArgs(Map.of(TARGET_ARG, ROOT_TARGET));
              })
          .withExporter(
              ES_MERGE_EXPORTER_ID,
              cfg -> {
                cfg.setClassName(ElasticsearchExporter.class.getName());
                cfg.setArgs(
                    Map.of(
                        "url", ES_URL,
                        "bulk", Map.of("size", 1, "delay", 1),
                        "index", Map.of("prefix", ROOT_INDEX_PREFIX)));
              })
          .withExporter(
              NARROWED_EXPORTER_ID,
              cfg -> cfg.setClassName(ProcessIdRecordingExporter.class.getName()))
          .withPtConfig(
              TENANT_A,
              camunda -> {
                final var exporter = new io.camunda.configuration.Exporter();
                exporter.setClassName(LocationRecordingExporter.class.getName());
                exporter.setArgs(Map.of(TARGET_ARG, TENANT_A_TARGET));
                camunda.getData().getExporters().put(LOCATION_EXPORTER_ID, exporter);

                // partial override: no className, no url, no bulk tuning — only the index prefix
                final var esOverride = new io.camunda.configuration.Exporter();
                esOverride.setArgs(Map.of("index", Map.of("prefix", TENANT_A_INDEX_PREFIX)));
                camunda.getData().getExporters().put(ES_MERGE_EXPORTER_ID, esOverride);

                final var tenantOnlyExporter = new io.camunda.configuration.Exporter();
                tenantOnlyExporter.setClassName(TenantAExporter.class.getName());
                camunda.getData().getExporters().put(TENANT_A_ONLY_EXPORTER_ID, tenantOnlyExporter);
              })
          // tenantA's complete generic-exporter manifest (ADR-0008 D1): it assigns every id it
          // touches — the redeclared location exporter, the partially-overridden ES exporter, and
          // its tenant-private exporter — but deliberately omits NARROWED_EXPORTER_ID. Bound via
          // the
          // Binder (never a POJO field); ignored until the assignment step is wired in (#56652).
          .withProperty(
              "camunda.physical-tenants.tenanta.data.exporters-assigned[0]", LOCATION_EXPORTER_ID)
          .withProperty(
              "camunda.physical-tenants.tenanta.data.exporters-assigned[1]", ES_MERGE_EXPORTER_ID)
          .withProperty(
              "camunda.physical-tenants.tenanta.data.exporters-assigned[2]",
              TENANT_A_ONLY_EXPORTER_ID);

  @AutoClose private CamundaClient tenantAClient;
  @AutoClose private CamundaClient defaultClient;

  @BeforeEach
  void setUp() {
    LocationRecordingExporter.reset();
    TenantAExporter.reset();
    ProcessIdRecordingExporter.reset();
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build();
    defaultClient =
        TENANTS.newClientBuilder(broker, PhysicalTenantsITHelper.DEFAULT_TENANT_ID).build();
  }

  /**
   * Asserts that a root-declared exporter runs on both tenants' partition groups, and that each
   * partition group sees the exporter args of its own tenant: the default tenant exports to the
   * root-configured target, while tenantA exports to the target redeclared under {@code
   * camunda.physical-tenants.tenanta.data.exporters.*}.
   */
  @Test
  void shouldApplyPerTenantExporterArgsOverride() {
    // given — deploy a process to each tenant so records flow through both partition groups
    deployAndCreateInstance(defaultClient, "default-process");
    deployAndCreateInstance(tenantAClient, "tenanta-process");

    // then — the exporter instance on the default partition group was configured with the root
    // target and received records, and the instance on tenantA's partition group was configured
    // with tenantA's overridden target and received records.
    await("exporter receives records under both the root and the tenantA target")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(LocationRecordingExporter.RECORDS_BY_TARGET)
                  .containsKey(ROOT_TARGET)
                  .containsKey(TENANT_A_TARGET);
              assertThat(LocationRecordingExporter.RECORDS_BY_TARGET.get(ROOT_TARGET)).isNotEmpty();
              assertThat(LocationRecordingExporter.RECORDS_BY_TARGET.get(TENANT_A_TARGET))
                  .isNotEmpty();
            });

    // and no exporter instance was configured with anything but the two expected targets
    assertThat(LocationRecordingExporter.RECORDS_BY_TARGET.keySet())
        .containsOnly(ROOT_TARGET, TENANT_A_TARGET);
  }

  /**
   * Asserts that an exporter declared only in {@code tenantA}'s config (absent from the root
   * config) is opened and receives records from {@code tenantA}'s partition group.
   *
   * <p>Disabled: the initial cluster configuration that decides which exporter ids are enabled per
   * partition is still generated from the root {@code BrokerCfg} only
   * (StaticConfigurationGenerator), so a tenant-only exporter is never enabled even though it is
   * present in the tenant's ExporterRepository. To be re-enabled once the cluster-configuration
   * layer supports per-physical-tenant exporters.
   */
  @Disabled(
      "Tenant-only exporters require per-physical-tenant exporter support in the"
          + " cluster-configuration layer (initial DynamicPartitionConfig is generated from the"
          + " root BrokerCfg only); tracked in"
          + " https://github.com/camunda/camunda/issues/56652")
  @Test
  void shouldApplyExporterConfiguredOnlyForPhysicalTenant() {
    // given — records flow through tenantA's partition group
    deployAndCreateInstance(tenantAClient, "tenanta-only-process");

    // then — the exporter configured only for tenantA should be opened on tenantA's partition and
    // should receive exported records
    await("tenant-only exporter is opened and receives records from tenantA's partition")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(TenantAExporter.RECORDS).isNotEmpty());
  }

  /**
   * Asserts the ADR-0008 D1 narrowing effect: a root-declared exporter that tenantA's {@code
   * exporters-assigned} manifest omits ({@link #NARROWED_EXPORTER_ID}) must run on the default
   * tenant's partition group but not on tenantA's — tenantA's records must never reach it.
   *
   * <p>Disabled: assignment is not wired into {@code PhysicalTenantResolver}, and even if it were,
   * narrowing an exporter out of a tenant's resolved config while the initial {@code
   * DynamicPartitionConfig} still enables it from the root {@code BrokerCfg}
   * (StaticConfigurationGenerator) would leave it enabled-but-config-less on tenantA's partitions
   * and turn it into a {@code BlockingExporter}. Until then the interim inherit-all behavior stands
   * (the exporter runs on both groups). To be re-enabled once the cluster-configuration layer
   * derives per-partition exporter enable state per tenant.
   */
  @Disabled(
      "Narrowing a root-declared exporter out of a physical tenant requires per-physical-tenant"
          + " exporter support in the cluster-configuration layer (initial DynamicPartitionConfig"
          + " is generated from the root BrokerCfg only), and the assignment step is not yet wired"
          + " into PhysicalTenantResolver; tracked in"
          + " https://github.com/camunda/camunda/issues/56652")
  @Test
  void shouldNarrowAwayUnassignedRootExporterForTenant() {
    // given — records flow through both tenants' partition groups under distinct process ids
    deployAndCreateInstance(defaultClient, DEFAULT_NARROW_PROCESS);
    deployAndCreateInstance(tenantAClient, TENANT_A_NARROW_PROCESS);

    // then — the narrowed exporter (still assigned implicitly on the exempt default tenant)
    // received
    // the default tenant's process
    await("narrowed exporter receives the default tenant's process")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(ProcessIdRecordingExporter.SEEN_PROCESS_IDS)
                    .contains(DEFAULT_NARROW_PROCESS));

    // and tenantA's process never reaches it, because the exporter was narrowed away for tenantA.
    // Await the absence for a window rather than asserting it once: the exporter thread is async,
    // so
    // an immediate check could pass before tenantA's records would otherwise have arrived.
    await("tenantA's process never reaches the narrowed exporter")
        .during(Duration.ofSeconds(5))
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(ProcessIdRecordingExporter.SEEN_PROCESS_IDS)
                    .doesNotContain(TENANT_A_NARROW_PROCESS));
  }

  /**
   * Asserts the ADR-0008 merge path end-to-end with the real ServiceLoader discovery on a real
   * broker classpath: the root declares an Elasticsearch exporter (url, bulk tuning, index prefix),
   * and tenantA overrides <b>only</b> {@code args.index.prefix}. Because the exporter's class ships
   * an {@code ExporterConfigMerger}, tenantA's partition group must run the exporter with the
   * root's url and bulk tuning but its own index prefix — records of both tenants land in the same
   * cluster under their respective prefixes, and neither tenant's records leak into the other's
   * indices.
   */
  @Test
  void shouldDeepMergePartialExporterArgsOverride() throws Exception {
    // given — records flow through both tenants' partition groups
    deployAndCreateInstance(defaultClient, "default-merge-process");
    deployAndCreateInstance(tenantAClient, "tenanta-merge-process");

    // then — the default partition group exports under the root prefix, and tenantA's partition
    // group exports under its overridden prefix to the root-configured url
    await("both tenants' records are exported under their own index prefix")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              assertThat(countDocuments(ROOT_INDEX_PREFIX, "default-merge-process")).isPositive();
              assertThat(countDocuments(TENANT_A_INDEX_PREFIX, "tenanta-merge-process"))
                  .isPositive();
            });

    // and neither tenant's records were exported under the other tenant's prefix
    assertThat(countDocuments(ROOT_INDEX_PREFIX, "tenanta-merge-process")).isZero();
    assertThat(countDocuments(TENANT_A_INDEX_PREFIX, "default-merge-process")).isZero();
  }

  /** Counts documents matching the process id across all indices under the given prefix. */
  private static long countDocuments(final String indexPrefix, final String processId)
      throws IOException, InterruptedException {
    // the zeebe ES exporter indexes raw records: the process id sits under value.bpmnProcessId
    final var query =
        URLEncoder.encode("value.bpmnProcessId:\"" + processId + "\"", StandardCharsets.UTF_8);
    final var request =
        HttpRequest.newBuilder(URI.create(ES_URL + "/" + indexPrefix + "*/_count?q=" + query))
            .GET()
            .build();
    final var response = HTTP.send(request, BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return MAPPER.readTree(response.body()).path("count").asLong();
  }

  private static void deployAndCreateInstance(final CamundaClient client, final String processId) {
    final var process = Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();

    // the tenant's Raft group may need a moment to elect a leader after startup
    await("deployment succeeds for " + processId)
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newDeployResourceCommand()
                            .addProcessModel(process, processId + ".bpmn")
                            .send()
                            .join()
                            .getProcesses())
                    .isNotEmpty());

    client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
  }

  /**
   * A minimal {@link Exporter} that records exported record positions keyed by the {@code target}
   * arg it was configured with. Static state so assertions can observe it from outside the exporter
   * lifecycle; call {@link #reset()} in {@code @BeforeEach} to isolate test runs.
   */
  public static final class LocationRecordingExporter implements Exporter {

    static final Map<String, CopyOnWriteArrayList<Long>> RECORDS_BY_TARGET =
        new ConcurrentHashMap<>();

    private String target;
    private Controller controller;

    static void reset() {
      RECORDS_BY_TARGET.clear();
    }

    @Override
    public void configure(final Context context) {
      target = String.valueOf(context.getConfiguration().getArguments().get(TARGET_ARG));
    }

    @Override
    public void open(final Controller controller) {
      this.controller = controller;
    }

    @Override
    public void export(final Record<?> record) {
      RECORDS_BY_TARGET
          .computeIfAbsent(target, ignored -> new CopyOnWriteArrayList<>())
          .add(record.getPosition());
      controller.updateLastExportedRecordPosition(record.getPosition());
    }
  }

  /**
   * A minimal {@link Exporter} used by the disabled tenant-only test. Records whether it was opened
   * and collects exported record positions.
   */
  public static final class TenantAExporter implements Exporter {

    static final AtomicBoolean OPENED = new AtomicBoolean(false);
    static final CopyOnWriteArrayList<Long> RECORDS = new CopyOnWriteArrayList<>();

    private Controller controller;

    static void reset() {
      OPENED.set(false);
      RECORDS.clear();
    }

    @Override
    public void configure(final Context context) {}

    @Override
    public void open(final Controller controller) {
      this.controller = controller;
      OPENED.set(true);
    }

    @Override
    public void export(final Record<?> record) {
      RECORDS.add(record.getPosition());
      controller.updateLastExportedRecordPosition(record.getPosition());
    }
  }

  /**
   * A minimal {@link Exporter} used by the disabled narrowing test. Records the {@code
   * bpmnProcessId} of every process-instance record it sees, so a test can assert which tenants'
   * partition groups it ran on.
   */
  public static final class ProcessIdRecordingExporter implements Exporter {

    static final Set<String> SEEN_PROCESS_IDS = ConcurrentHashMap.newKeySet();

    private Controller controller;

    static void reset() {
      SEEN_PROCESS_IDS.clear();
    }

    @Override
    public void configure(final Context context) {}

    @Override
    public void open(final Controller controller) {
      this.controller = controller;
    }

    @Override
    public void export(final Record<?> record) {
      if (record.getValue() instanceof final ProcessInstanceRecordValue processInstance) {
        SEEN_PROCESS_IDS.add(processInstance.getBpmnProcessId());
      }
      controller.updateLastExportedRecordPosition(record.getPosition());
    }
  }
}
