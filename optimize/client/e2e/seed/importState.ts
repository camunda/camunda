/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {env} from '../env';
import {countOrders, INCIDENT_PROCESS, ORDER_PROCESS} from './dataset';

// Reads what Optimize has imported so far, straight from its indices.
export async function readImportState() {
  const orders = `optimize-process-instance-${ORDER_PROCESS.key}`;
  const incidents = `optimize-process-instance-${INCIDENT_PROCESS.key}`;

  return {
    definitions: await count('optimize-process-definition', {
      terms: {key: [ORDER_PROCESS.key, INCIDENT_PROCESS.key]},
    }),
    completedOrders: await count(orders, {term: {state: 'COMPLETED'}}),
    runningOrders: await count(orders, {term: {state: 'ACTIVE'}}),
    canceledOrders: await count(orders, {term: {state: 'EXTERNALLY_TERMINATED'}}),
    ordersWithVariables: await count(
      orders,
      nested('variables', {term: {'variables.name': 'category'}})
    ),
    ordersWithAssignee: await count(
      orders,
      nested('flowNodeInstances', {exists: {field: 'flowNodeInstances.assignee'}})
    ),
    openIncidents: await count(
      incidents,
      nested('incidents', {term: {'incidents.incidentStatus': 'open'}})
    ),
    resolvedIncidents: await count(
      incidents,
      nested('incidents', {term: {'incidents.incidentStatus': 'resolved'}})
    ),
  };
}

export function expectedImportState(): Awaited<ReturnType<typeof readImportState>> {
  return {
    definitions: ORDER_PROCESS.resources.length + INCIDENT_PROCESS.resources.length,
    completedOrders: countOrders((order) => order.outcome === 'completed'),
    runningOrders: countOrders((order) => order.outcome === 'running'),
    canceledOrders: countOrders((order) => order.outcome === 'canceled'),
    ordersWithVariables: countOrders(),
    ordersWithAssignee: countOrders((order) => order.assignee !== undefined),
    openIncidents: INCIDENT_PROCESS.instances.filter(({incident}) => incident === 'open').length,
    resolvedIncidents: INCIDENT_PROCESS.instances.filter(({incident}) => incident === 'resolved')
      .length,
  };
}

function nested(path: string, query: object) {
  return {nested: {path, query}};
}

async function count(index: string, query: object): Promise<number> {
  const response = await fetch(`${env.databaseUrl}/${index}/_count`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({query}),
  });
  // The index only appears once Optimize imported the first document.
  if (response.status === 404) {
    return 0;
  }
  if (!response.ok) {
    throw new Error(`Counting ${index} failed: ${response.status} ${await response.text()}`);
  }
  const {count} = (await response.json()) as {count: number};
  return count;
}
