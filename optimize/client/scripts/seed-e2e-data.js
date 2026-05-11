/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Deploys the minimal set of BPMN definitions required by the legacy
// optimize/client/e2e/sm-tests after the optimize/qa/data-generation Maven
// module was removed in commit 90e86547846 (Jul 2024). Without these
// deployments the sm-tests fail searching for definitions like "Order
// process", "Only Incidents Process", "Big variable process", "bigProcess"
// and "complexProcess" in Optimize's dropdowns.

import {Camunda8} from '@camunda8/sdk';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const demoDir = resolve(__dirname, '..', 'demo-data');

const c8 = new Camunda8({
  ZEEBE_GRPC_ADDRESS: process.env.ZEEBE_GRPC_ADDRESS || 'grpc://localhost:26500',
  CAMUNDA_OAUTH_DISABLED: true,
  CAMUNDA_SECURE_CONNECTION: false,
  // @ts-expect-error - permitWithoutCalls flag accepted by gRPC but not typed
  GRPC_KEEPALIVE_PERMIT_WITHOUT_CALLS: 0,
});
const zbc = c8.getZeebeGrpcApiClient();

async function deploy(file) {
  const path = resolve(demoDir, file);
  const result = await zbc.deployResource({processFilename: path});
  const key = result?.deployments?.[0]?.process?.bpmnProcessId || file;
  console.log(`[seed] deployed ${file} (${key})`);
}

async function start(bpmnProcessId, variables = {}) {
  const result = await zbc.createProcessInstance({bpmnProcessId, variables});
  console.log(`[seed] started ${bpmnProcessId} (key=${result.processInstanceKey})`);
  return result;
}

function buildBigVariables() {
  const vars = {};
  for (let i = 0; i < 20; i++) {
    vars[`var${i}`] = `value-${i}`;
  }
  return vars;
}

async function main() {
  console.log('[seed] connecting to Zeebe at', process.env.ZEEBE_GRPC_ADDRESS || 'grpc://localhost:26500');

  // 1. Deploy all five BPMNs.
  await deploy('orderProcess.bpmn');
  await deploy('onlyIncidentsProcess.bpmn');
  await deploy('bigVariableProcess.bpmn');
  await deploy('bigProcess.bpmn');
  await deploy('complexProcess.bpmn');

  // 2. Register workers BEFORE creating instances so the jobs are picked up
  //    promptly. The Order process workers complete service tasks so the
  //    instances run to end and provide non-trivial duration data for the
  //    aggregator/heatmap reports. The Only-Incidents worker fails the job
  //    with zero retries to generate an unresolved incident.
  zbc.createWorker({
    taskType: 'checkPayment',
    taskHandler: (job) => job.complete({paymentOk: true}),
  });
  zbc.createWorker({
    taskType: 'shipArticles',
    taskHandler: (job) => job.complete({shipped: true}),
  });
  zbc.createWorker({
    taskType: 'requestForPayment',
    taskHandler: (job) => job.complete({paymentRequested: true}),
  });
  zbc.createWorker({
    taskType: 'alwaysFails',
    taskHandler: (job) => job.fail({errorMessage: 'always fails', retries: 0}),
  });

  // 3. Start instances.
  for (let i = 0; i < 5; i++) {
    await start('orderProcess', {
      amount: 100 + i * 10,
      customer: `cust-${i}`,
      paymentOk: i % 2 === 0,
    });
  }
  for (let i = 0; i < 3; i++) {
    await start('onlyIncidentsProcess');
  }
  for (let i = 0; i < 2; i++) {
    await start('bigVariableProcess', buildBigVariables());
  }
  await start('bigProcess');
  await start('complexProcess');

  // 4. Allow workers some time to process jobs (complete or fail) before
  //    closing the client. Workers run asynchronously, so without this wait
  //    most jobs would still be queued when the script exits.
  await new Promise((r) => setTimeout(r, 15000));

  await zbc.close();
  console.log('[seed] done');
}

main().catch((err) => {
  console.error('[seed] failed:', err);
  process.exit(1);
});
