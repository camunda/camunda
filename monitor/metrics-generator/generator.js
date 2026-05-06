'use strict';
/**
 * Synthetic Prometheus metrics for local Camunda development.
 *
 * Only produces metrics the real Camunda container (camunda:9600) does NOT
 * expose — namely Kubernetes/infrastructure metrics and dashboard template
 * variable seeds. Application metrics (JVM, Zeebe engine, Operate, Tasklist)
 * that the real container already emits are intentionally absent here to
 * avoid double-counting in Grafana panels.
 *
 * Run scripts/check-metrics.js after starting the stack to verify coverage.
 *
 * Exposes on :9400/metrics.
 */
const client = require('prom-client');
const http   = require('http');

const PORT    = 9400;
const TICK_MS = 5_000;

const ENVIRONMENTS = [
  { namespace: 'local',      cluster: 'local',       region: 'eu-west-1',      geoArea: 'eu', provider: 'gcp',   generation: 'stable', salesPlan: 'enterprise', _esBytes: 8  * (1 << 30) },
  { namespace: 'staging',    cluster: 'staging-eu',  region: 'us-east-1',      geoArea: 'us', provider: 'aws',   generation: 'alpha',  salesPlan: 'trial',      _esBytes: 4  * (1 << 30) },
  { namespace: 'production', cluster: 'prod-apac',   region: 'ap-southeast-1', geoArea: 'ap', provider: 'azure', generation: 'stable', salesPlan: 'enterprise', _esBytes: 20 * (1 << 30) },
];

const register = new client.Registry();

// ── Static configuration ──────────────────────────────────────────────────────

const SERVICES = [
  { pod: 'zeebe-0',    application: 'zeebe',         container: 'zeebe',    image: 'camunda/zeebe:latest',    cpuLimit: 2,   memLimit: 2 * (1 << 30) },
  { pod: 'operate-0',  application: 'operate',       container: 'operate',  image: 'camunda/operate:latest',  cpuLimit: 1,   memLimit: 1 * (1 << 30) },
  { pod: 'tasklist-0', application: 'tasklist',      container: 'tasklist', image: 'camunda/tasklist:latest', cpuLimit: 1,   memLimit: 1 * (1 << 30) },
  { pod: 'gateway-0',  application: 'zeebe-gateway', container: 'gateway',  image: 'camunda/zeebe:latest',    cpuLimit: 0.5, memLimit: 512 * (1 << 20) },
];

const PARTITIONS = ['1', '2', '3'];

const GRPC_METHODS = [
  'DeployResource', 'CreateProcessInstance', 'ActivateJobs',
  'CompleteJob', 'FailJob', 'PublishMessage', 'SetVariables',
  'ResolveIncident', 'CancelProcessInstance', 'StreamActivatedJobs',
];

const ELEMENT_TYPES   = ['PROCESS', 'SERVICE_TASK', 'START_EVENT', 'END_EVENT'];
const ELEMENT_INTENTS = ['ELEMENT_ACTIVATING', 'ELEMENT_ACTIVATED', 'ELEMENT_COMPLETING', 'ELEMENT_COMPLETED'];

// ── Metric factory helpers ────────────────────────────────────────────────────

const g = (name, help, labelNames) =>
  new client.Gauge({ name, help, labelNames, registers: [register] });
const c = (name, help, labelNames) =>
  new client.Counter({ name, help, labelNames, registers: [register] });
const histo = (name, help, labelNames, buckets) =>
  new client.Histogram({ name, help, labelNames, buckets: buckets || client.exponentialBuckets(0.001, 2, 14), registers: [register] });

// ── Template variable seed ────────────────────────────────────────────────────
// The namespace/geo_area/cloud_provider/generation/sales_plan_type variable
// chain in the core-features dashboard cascades through kube_namespace_labels.

const kubeNamespaceLabels = g('kube_namespace_labels',
  'Kubernetes namespace labels (synthetic)',
  ['namespace', 'camunda_geo_area', 'camunda_provider',
   'label_cloud_camunda_io_generation', 'label_cloud_camunda_io_sales_plan_type']);

// ── gRPC — Histogram so _bucket/_count/_sum are present ──────────────────────

const grpcDuration = histo('grpc_server_processing_duration_seconds',
  'gRPC server processing duration',
  ['namespace', 'cluster', 'pod', 'method', 'statusCode', 'camunda_region'],
  [0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1, 5]);

// ── Infrastructure base metrics ───────────────────────────────────────────────
// container_* feed the Prometheus recording rules (recording_rules.yml) that
// produce node_namespace_pod_container:* metrics for infrastructure panels.
// image label must be non-empty for the {image!=""} recording rule filter.

const containerCpuUsage = c('container_cpu_usage_seconds_total', 'Container CPU seconds',
  ['cluster', 'namespace', 'pod', 'container', 'image']);

const containerMemWorkingSet = g('container_memory_working_set_bytes', 'Container memory working set',
  ['cluster', 'namespace', 'pod', 'container', 'image']);

const kubePodContainerStatusReady = g('kube_pod_container_status_ready', 'Pod container ready',
  ['namespace', 'pod', 'container', 'camunda_region']);

const kubePodContainerResourceLimits = g('kube_pod_container_resource_limits', 'Pod container resource limits',
  ['namespace', 'pod', 'container', 'resource', 'unit', 'cluster', 'camunda_geo_area', 'camunda_provider']);

const kubeletVolumeStatsUsed     = g('kubelet_volume_stats_used_bytes',     'PVC used bytes',
  ['namespace', 'persistentvolumeclaim', 'camunda_geo_area', 'camunda_provider']);
const kubeletVolumeStatsCapacity = g('kubelet_volume_stats_capacity_bytes', 'PVC capacity bytes',
  ['namespace', 'persistentvolumeclaim', 'camunda_geo_area', 'camunda_provider']);

// ── Elasticsearch cluster health ──────────────────────────────────────────────

const esActiveShards   = g('elasticsearch_cluster_health_active_shards',    'ES active shards',
  ['cluster', 'namespace', 'camunda_geo_area', 'camunda_provider']);
const esNumberOfNodes  = g('elasticsearch_cluster_health_number_of_nodes',  'ES node count',
  ['cluster', 'namespace', 'camunda_geo_area', 'camunda_provider']);
const esIndexStoreSize = g('elasticsearch_indices_store_size_bytes_primary', 'ES index store size',
  ['namespace', 'camunda_geo_area', 'camunda_provider']);

// ── Zeebe — metrics NOT produced by the real Camunda container ────────────────

const zeebePartitionLoad  = g('zeebe_partition_load', 'Partition load',
  ['namespace', 'cluster', 'pod', 'partition']);
const zeebeRaftQueueDepth = g('zeebe_raft_append_entries_batch_queue_depth', 'Raft batch queue depth',
  ['namespace', 'cluster', 'pod', 'partition']);
const zeebeElementEvents  = c('zeebe_element_instance_events_total', 'Element events',
  ['namespace', 'cluster', 'pod', 'partition', 'type', 'intent']);
const zeebeCommandPending = g('zeebe_command_distributions_pending', 'Pending command distributions',
  ['namespace', 'cluster', 'pod', 'partition']);
const zeebeCommandRetries = c('zeebe_command_distributions_inflight_retries_total', 'Command distribution retries',
  ['namespace', 'cluster', 'pod', 'partition', 'camunda_geo_area', 'camunda_provider']);

// Legacy naming variants — some dashboard panels query both the _seconds and
// the bare name depending on the Zeebe version the dashboard was written for.
const zeebeSpLatencyLegacy  = histo('zeebe_stream_processor_latency', 'Stream processor latency (legacy name)',
  ['namespace', 'cluster', 'pod', 'partition'],
  [0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1]);
const zeebeJobActivation       = histo('zeebe_job_activation_time_seconds', 'Job activation time',
  ['namespace', 'cluster', 'pod', 'partition'],
  [0.001, 0.01, 0.1, 1, 10, 30, 60]);
const zeebeJobActivationLegacy = histo('zeebe_job_activation_time', 'Job activation time (legacy name)',
  ['namespace', 'cluster', 'pod', 'partition'],
  [0.001, 0.01, 0.1, 1, 10, 30, 60]);
const zeebePiExecTime       = histo('zeebe_process_instance_execution_time_seconds', 'Process instance execution time',
  ['namespace', 'cluster', 'pod', 'partition'],
  [0.01, 0.1, 1, 10, 60, 300, 1800, 3600]);
const zeebePiExecTimeLegacy = histo('zeebe_process_instance_execution_time', 'Process instance execution time (legacy name)',
  ['namespace', 'cluster', 'pod', 'partition'],
  [0.01, 0.1, 1, 10, 60, 300, 1800, 3600]);

// ── Camunda exporter — flush/RDBMS metrics not in real container ──────────────

const zeebeCamundaFlushSum   = c('zeebe_camunda_exporter_flush_latency_seconds_sum',   'Flush latency sum',
  ['namespace', 'cluster', 'pod', 'partition']);
const zeebeCamundaFlushCount = c('zeebe_camunda_exporter_flush_latency_seconds_count', 'Flush latency count',
  ['namespace', 'cluster', 'pod', 'partition']);
const zeebeEsFlushDuration   = histo('zeebe_elasticsearch_exporter_flush_duration_seconds', 'ES exporter flush duration',
  ['namespace', 'cluster', 'pod', 'partition', 'camunda_geo_area', 'camunda_provider'],
  [0.005, 0.01, 0.05, 0.1, 0.5, 1, 5]);
const zeebeRdbmsFlushSum     = c('zeebe_rdbms_exporter_flush_latency_seconds_sum',   'RDBMS flush latency sum',
  ['namespace', 'cluster', 'pod', 'partition']);
const zeebeRdbmsFlushCount   = c('zeebe_rdbms_exporter_flush_latency_seconds_count', 'RDBMS flush latency count',
  ['namespace', 'cluster', 'pod', 'partition']);

// ── Operate metrics not in real container ─────────────────────────────────────

const operateEvents       = c('operate_events_processed_total',           'Events processed',
  ['namespace', 'pod', 'partition', 'type', 'intent']);
const operateImportQueue  = g('operate_import_queue_size',                'Import queue size',
  ['namespace', 'pod', 'partition', 'camunda_geo_area', 'camunda_provider']);
const operateArchivedPi   = c('operate_archived_process_instances_total', 'Archived PIs',
  ['namespace', 'pod']);
const operatePostImporter = g('operate_post_importer_queue_size',         'Post importer queue size',
  ['namespace', 'pod', 'camunda_geo_area', 'camunda_provider']);
const operateImportTimeCount  = c('operate_import_time_seconds_count', 'Import time count',
  ['namespace', 'pod', 'camunda_geo_area', 'camunda_provider']);
const operateImportTimeSum    = c('operate_import_time_seconds_sum',   'Import time sum',
  ['namespace', 'pod', 'camunda_geo_area', 'camunda_provider']);
const operateArchiverQueryCount   = c('operate_archiver_query_seconds_count',         'Archiver query count',   ['namespace', 'pod']);
const operateArchiverQuerySum     = c('operate_archiver_query_seconds_sum',           'Archiver query sum',     ['namespace', 'pod']);
const operateArchiverReindexCount = c('operate_archiver_reindex_query_seconds_count', 'Archiver reindex count', ['namespace', 'pod']);
const operateArchiverReindexSum   = c('operate_archiver_reindex_query_seconds_sum',   'Archiver reindex sum',   ['namespace', 'pod']);
const operateArchiverDeleteCount  = c('operate_archiver_delete_query_seconds_count',  'Archiver delete count',  ['namespace', 'pod']);
const operateArchiverDeleteSum    = c('operate_archiver_delete_query_seconds_sum',    'Archiver delete sum',    ['namespace', 'pod']);

// ── Tasklist metrics not in real container ────────────────────────────────────

const tasklistEvents          = c('tasklist_events_processed_total',           'Events processed',
  ['namespace', 'pod', 'partition', 'type', 'intent']);
const tasklistArchivedPi      = c('tasklist_archived_process_instances_total', 'Archived PIs',
  ['namespace', 'pod']);
const tasklistImportTimeCount = c('tasklist_import_time_seconds_count', 'Import time count',
  ['namespace', 'pod', 'camunda_geo_area', 'camunda_provider']);
const tasklistImportTimeSum   = c('tasklist_import_time_seconds_sum',   'Import time sum',
  ['namespace', 'pod', 'camunda_geo_area', 'camunda_provider']);

// ── Helpers ───────────────────────────────────────────────────────────────────

const randInt = (lo, hi) => Math.floor(Math.random() * (hi - lo + 1)) + lo;
const strHash = (s) => s.split('').reduce((a, ch) => a + ch.charCodeAt(0), 0);

// ── Seed all label combinations ───────────────────────────────────────────────

function seed() {
  for (const env of ENVIRONMENTS) {
    const { namespace, cluster, region, geoArea: geo, provider, generation, salesPlan } = env;

    kubeNamespaceLabels.set({
      namespace,
      camunda_geo_area:                       geo,
      camunda_provider:                       provider,
      label_cloud_camunda_io_generation:      generation,
      label_cloud_camunda_io_sales_plan_type: salesPlan,
    }, 1);

    for (const { pod, container, image, cpuLimit, memLimit } of SERVICES) {
      const cLbl = { cluster, namespace, pod, container, image };
      containerCpuUsage.inc(cLbl, 0);
      containerMemWorkingSet.set(cLbl, Math.floor(memLimit * 0.4));
      kubePodContainerStatusReady.set({ namespace, pod, container, camunda_region: region }, 1);
      kubePodContainerResourceLimits.set(
        { namespace, pod, container, resource: 'cpu',    unit: 'core', cluster, camunda_geo_area: geo, camunda_provider: provider },
        cpuLimit);
      kubePodContainerResourceLimits.set(
        { namespace, pod, container, resource: 'memory', unit: 'byte', cluster, camunda_geo_area: geo, camunda_provider: provider },
        memLimit);
    }

    // gRPC
    const gwLbl = { namespace, cluster, pod: 'gateway-0', camunda_region: region };
    for (const method of GRPC_METHODS) {
      grpcDuration.observe({ ...gwLbl, method, statusCode: 'OK' }, 0.002);
    }

    // Elasticsearch PVC
    const pvcLbl = { namespace, persistentvolumeclaim: 'elasticsearch-0', camunda_geo_area: geo, camunda_provider: provider };
    kubeletVolumeStatsCapacity.set(pvcLbl, 32 * (1 << 30));
    kubeletVolumeStatsUsed.set(pvcLbl, env._esBytes);

    // Elasticsearch cluster health
    const esLbl = { cluster, namespace, camunda_geo_area: geo, camunda_provider: provider };
    esActiveShards.set(esLbl, 30);
    esNumberOfNodes.set(esLbl, 3);
    esIndexStoreSize.set({ namespace, camunda_geo_area: geo, camunda_provider: provider }, env._esBytes);

    // Zeebe per-partition
    const zBase = { namespace, cluster, pod: 'zeebe-0' };
    for (const partition of PARTITIONS) {
      const zp    = { ...zBase, partition };
      const zpGeo = { ...zp, camunda_geo_area: geo, camunda_provider: provider };

      zeebePartitionLoad.set(zp, 0);
      zeebeRaftQueueDepth.set(zp, 0);
      zeebeCommandPending.set(zp, 0);
      zeebeCommandRetries.inc(zpGeo, 0);
      zeebeCamundaFlushSum.inc(zp, 0);
      zeebeCamundaFlushCount.inc(zp, 0);
      zeebeRdbmsFlushSum.inc(zp, 0);
      zeebeRdbmsFlushCount.inc(zp, 0);

      for (const type of ELEMENT_TYPES) {
        for (const intent of ELEMENT_INTENTS) {
          zeebeElementEvents.inc({ ...zp, type, intent }, 0);
        }
      }
    }

    // Operate
    const oPod  = 'operate-0';
    const oBase = { namespace, pod: oPod };
    const oGeo  = { ...oBase, camunda_geo_area: geo, camunda_provider: provider };
    operateArchivedPi.inc(oBase, 0);
    operatePostImporter.set(oGeo, 0);
    operateImportTimeCount.inc(oGeo, 0);
    operateImportTimeSum.inc(oGeo, 0);
    operateArchiverQueryCount.inc(oBase, 0);
    operateArchiverQuerySum.inc(oBase, 0);
    operateArchiverReindexCount.inc(oBase, 0);
    operateArchiverReindexSum.inc(oBase, 0);
    operateArchiverDeleteCount.inc(oBase, 0);
    operateArchiverDeleteSum.inc(oBase, 0);
    for (const partition of PARTITIONS) {
      const op = { ...oBase, partition };
      operateImportQueue.set({ ...op, camunda_geo_area: geo, camunda_provider: provider }, 0);
      for (const type of ELEMENT_TYPES) {
        for (const intent of ELEMENT_INTENTS) {
          operateEvents.inc({ ...op, type, intent }, 0);
        }
      }
    }

    // Tasklist
    const tPod  = 'tasklist-0';
    const tBase = { namespace, pod: tPod };
    const tGeo  = { ...tBase, camunda_geo_area: geo, camunda_provider: provider };
    tasklistArchivedPi.inc(tBase, 0);
    tasklistImportTimeCount.inc(tGeo, 0);
    tasklistImportTimeSum.inc(tGeo, 0);
    for (const partition of PARTITIONS) {
      for (const type of ELEMENT_TYPES) {
        for (const intent of ELEMENT_INTENTS) {
          tasklistEvents.inc({ namespace, pod: tPod, partition, type, intent }, 0);
        }
      }
    }
  }
}

// ── Periodic update ───────────────────────────────────────────────────────────

function tick() {
  const now = Date.now() / 1000;

  for (const env of ENVIRONMENTS) {
    const { namespace, cluster, region, geoArea: geo, provider } = env;

    for (const { pod, container, image, memLimit } of SERVICES) {
      const cLbl = { cluster, namespace, pod, container, image };
      const cpu   = 0.06 + 0.04 * Math.sin(now / 47);
      containerCpuUsage.inc(cLbl, cpu * TICK_MS / 1000);
      const memPhase = Math.sin(now / 30 + strHash(pod) % 5) * 0.1;
      containerMemWorkingSet.set(cLbl, Math.max(0, memLimit * (0.4 + memPhase)));
    }

    // gRPC
    const gwLbl = { namespace, cluster, pod: 'gateway-0', camunda_region: region };
    for (const method of GRPC_METHODS) {
      const n = randInt(1, 15);
      for (let i = 0; i < n; i++) {
        grpcDuration.observe({ ...gwLbl, method, statusCode: 'OK' }, -Math.log(Math.random()) / 5);
      }
      if (Math.random() < 0.02) {
        grpcDuration.observe({ ...gwLbl, method, statusCode: 'RESOURCE_EXHAUSTED' }, 0.5);
      }
    }

    // Elasticsearch PVC + cluster health
    env._esBytes = Math.min(env._esBytes + randInt(100_000, 500_000), 28 * (1 << 30));
    const pvcLbl = { namespace, persistentvolumeclaim: 'elasticsearch-0', camunda_geo_area: geo, camunda_provider: provider };
    kubeletVolumeStatsUsed.set(pvcLbl, env._esBytes);
    const esLbl = { cluster, namespace, camunda_geo_area: geo, camunda_provider: provider };
    esActiveShards.set(esLbl, 30 + randInt(-2, 2));
    esNumberOfNodes.set(esLbl, 3);
    esIndexStoreSize.set({ namespace, camunda_geo_area: geo, camunda_provider: provider }, env._esBytes * 1.2);

    // Zeebe per-partition
    const zBase = { namespace, cluster, pod: 'zeebe-0' };
    for (const partition of PARTITIONS) {
      const zp    = { ...zBase, partition };
      const zpGeo = { ...zp, camunda_geo_area: geo, camunda_provider: provider };

      zeebePartitionLoad.set(zp, 0.1 + Math.random() * 0.3);
      zeebeRaftQueueDepth.set(zp, randInt(0, 5));
      zeebeCommandPending.set(zp, randInt(0, 3));
      zeebeCommandRetries.inc(zpGeo, randInt(0, 1));

      const flushMs = 0.001 + Math.random() * 0.05;
      zeebeCamundaFlushSum.inc(zp, flushMs);
      zeebeCamundaFlushCount.inc(zp, 1);
      zeebeEsFlushDuration.observe(zpGeo, 0.005 + Math.random() * 0.02);
      zeebeRdbmsFlushSum.inc(zp, 0.001 + Math.random() * 0.005);
      zeebeRdbmsFlushCount.inc(zp, 1);

      zeebeSpLatencyLegacy.observe(zp, Math.random() * 0.02);
      zeebeJobActivation.observe(zp, Math.random() * 0.5);
      zeebeJobActivationLegacy.observe(zp, Math.random() * 0.5);
      if (Math.random() < 0.5) {
        zeebePiExecTime.observe(zp, 1 + Math.random() * 60);
        zeebePiExecTimeLegacy.observe(zp, 1 + Math.random() * 60);
      }

      for (const type of ELEMENT_TYPES) {
        for (const intent of ELEMENT_INTENTS) {
          zeebeElementEvents.inc({ ...zp, type, intent }, randInt(0, 3));
        }
      }
    }

    // Operate
    const oPod  = 'operate-0';
    const oBase = { namespace, pod: oPod };
    const oGeo  = { ...oBase, camunda_geo_area: geo, camunda_provider: provider };
    for (const partition of PARTITIONS) {
      const op = { ...oBase, partition };
      operateImportQueue.set({ ...op, camunda_geo_area: geo, camunda_provider: provider }, randInt(0, 10));
      operateEvents.inc({ ...op, type: 'PROCESS', intent: 'ELEMENT_COMPLETED' }, randInt(1, 5));
    }
    if (Math.random() < 0.3) operateArchivedPi.inc(oBase, 1);
    operatePostImporter.set(oGeo, randInt(0, 5));
    operateImportTimeCount.inc(oGeo, randInt(1, 5));
    operateImportTimeSum.inc(oGeo, Math.random() * 0.5);
    const archiverLat = 0.01 + Math.random() * 0.1;
    operateArchiverQueryCount.inc(oBase, 1);
    operateArchiverQuerySum.inc(oBase, archiverLat);
    if (Math.random() < 0.3) {
      operateArchiverReindexCount.inc(oBase, 1);
      operateArchiverReindexSum.inc(oBase, archiverLat * 3);
      operateArchiverDeleteCount.inc(oBase, 1);
      operateArchiverDeleteSum.inc(oBase, archiverLat);
    }

    // Tasklist
    const tPod  = 'tasklist-0';
    const tBase = { namespace, pod: tPod };
    const tGeo  = { ...tBase, camunda_geo_area: geo, camunda_provider: provider };
    for (const partition of PARTITIONS) {
      tasklistEvents.inc({ namespace, pod: tPod, partition, type: 'PROCESS', intent: 'ELEMENT_COMPLETED' }, randInt(1, 4));
    }
    if (Math.random() < 0.3) tasklistArchivedPi.inc(tBase, 1);
    tasklistImportTimeCount.inc(tGeo, randInt(1, 5));
    tasklistImportTimeSum.inc(tGeo, Math.random() * 0.5);
  }
}

// ── V2 REST API traffic ───────────────────────────────────────────────────────
// Makes real requests to Camunda's V2 REST API so the container emits
// http_server_requests_seconds metrics for each endpoint.
// Camunda unavailability is silently ignored — the loop retries every tick.

const CAMUNDA_URL = process.env.CAMUNDA_URL || 'http://localhost:8080';
const API_TICK_MS = 15_000;

async function postSearch(path, filter = {}) {
  try {
    await fetch(`${CAMUNDA_URL}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify({ filter, page: { limit: 20 } }),
      signal: AbortSignal.timeout(5000),
    });
  } catch { /* not ready yet */ }
}

async function get(path) {
  try {
    await fetch(`${CAMUNDA_URL}${path}`, {
      headers: { 'Accept': 'application/json' },
      signal: AbortSignal.timeout(5000),
    });
  } catch { /* not ready yet */ }
}

async function apiTrafficTick() {
  // GET endpoints (no path params)
  await get('/v2/topology');
  await get('/v2/license');
  await get('/v2/status');
  await get('/v2/system/configuration');

  // POST search endpoints
  await postSearch('/v2/process-instances/search');
  await postSearch('/v2/process-instances/search', { state: 'ACTIVE' });
  await postSearch('/v2/process-instances/search', { state: 'INCIDENT' });
  await postSearch('/v2/process-definitions/search');
  await postSearch('/v2/incidents/search');
  await postSearch('/v2/user-tasks/search');
  await postSearch('/v2/user-tasks/search', { state: 'CREATED' });
  await postSearch('/v2/jobs/search');
  await postSearch('/v2/variables/search');
}

// ── HTTP server ───────────────────────────────────────────────────────────────

http.createServer(async (req, res) => {
  if (req.url === '/metrics') {
    res.setHeader('Content-Type', register.contentType);
    res.end(await register.metrics());
  } else {
    res.writeHead(200);
    res.end('metrics-generator ok\n');
  }
}).listen(PORT, () => {
  console.log(`Synthetic metrics generator listening on :${PORT}`);
  seed();
  console.log('Seeded. Entering update loop...');
  setInterval(tick, TICK_MS);
  setInterval(() => { apiTrafficTick().catch(() => {}); }, API_TICK_MS);
  apiTrafficTick().catch(() => {});
});
