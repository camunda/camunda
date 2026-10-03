/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {APIRequestContext, APIResponse} from '@playwright/test';

import {INCIDENT_PROCESS, ORDER_PROCESS} from '../seed/dataset';

// Thin client for Optimize's internal REST API, used to arrange test preconditions quickly.
// Anything a test verifies must still be driven through the UI.

type ProcessKey = typeof ORDER_PROCESS.key | typeof INCIDENT_PROCESS.key;

export type ReportData = {
  processKey?: ProcessKey;
  view?: {entity: string | null; properties: string[]};
  groupBy?: {type: string; value: unknown};
  visualization?: string;
  configuration?: Record<string, unknown>;
};

export type DashboardTile =
  {type: 'optimize_report'; reportId: string} | {type: 'text'; text: string};

export class OptimizeApi {
  private csrfToken?: string;

  constructor(private readonly request: APIRequestContext) {}

  async createCollection(name: string): Promise<string> {
    const {id} = await this.post<{id: string}>('/api/collection', {name});
    await this.put(`/api/collection/${id}/scope`, [
      scope(ORDER_PROCESS.key),
      scope(INCIDENT_PROCESS.key),
    ]);
    return id;
  }

  async deleteCollection(id: string): Promise<void> {
    await this.send('delete', `/api/collection/${id}?force=true`, undefined, [404]);
  }

  async createReport(
    name: string,
    collectionId: string | null,
    {
      processKey = ORDER_PROCESS.key,
      view = {entity: 'processInstance', properties: ['frequency']},
      groupBy = {type: 'none', value: null},
      visualization = 'number',
      configuration,
    }: ReportData = {}
  ): Promise<string> {
    const {id} = await this.post<{id: string}>('/api/report/process/single', {
      name,
      collectionId,
      data: {
        definitions: [
          {
            key: processKey,
            name: processName(processKey),
            displayName: processName(processKey),
            versions: ['all'],
            tenantIds: ['<default>'],
          },
        ],
        view,
        groupBy,
        visualization,
        ...(configuration && {configuration}),
      },
    });
    return id;
  }

  async createDashboard(
    name: string,
    collectionId: string | null,
    tiles: DashboardTile[] = []
  ): Promise<string> {
    const {id} = await this.post<{id: string}>('/api/dashboard', {
      name,
      collectionId,
      description: null,
      availableFilters: [],
      tiles: tiles.map((tile, index) => ({
        id: tile.type === 'optimize_report' ? tile.reportId : '',
        type: tile.type,
        position: {x: (index % 3) * 6, y: Math.floor(index / 3) * 4},
        dimensions: {width: 6, height: 4},
        configuration: tile.type === 'text' ? textTileConfiguration(tile.text) : null,
      })),
    });
    return id;
  }

  async createAlert(name: string, reportId: string): Promise<string> {
    const {id} = await this.post<{id: string}>('/api/alert', {
      name,
      reportId,
      threshold: 100,
      thresholdOperator: '>',
      checkInterval: {value: 10, unit: 'minutes'},
      reminder: null,
      fixNotification: false,
      emails: ['demo@example.com'],
    });
    return id;
  }

  private post<T>(url: string, data: unknown): Promise<T> {
    return this.send('post', url, data);
  }

  private put<T>(url: string, data: unknown): Promise<T> {
    return this.send('put', url, data);
  }

  private async send<T>(
    method: 'post' | 'put' | 'delete',
    url: string,
    data?: unknown,
    acceptedStatuses: number[] = []
  ): Promise<T> {
    const response = await this.request[method](url, {
      data,
      headers: {'X-CSRF-TOKEN': await this.getCsrfToken()},
    });
    return parse<T>(response, `${method.toUpperCase()} ${url}`, acceptedStatuses);
  }

  // Optimize hands the CSRF token out on any authenticated read.
  private async getCsrfToken(): Promise<string> {
    if (!this.csrfToken) {
      const response = await this.request.get('/api/identity/current/user');
      await parse(response, 'GET /api/identity/current/user');
      this.csrfToken = response.headers()['x-csrf-token'] ?? '';
    }
    return this.csrfToken;
  }
}

function scope(definitionKey: string) {
  return {definitionKey, definitionType: 'process', tenants: ['<default>']};
}

function processName(key: ProcessKey): string {
  return key === ORDER_PROCESS.key ? ORDER_PROCESS.name : INCIDENT_PROCESS.name;
}

function textTileConfiguration(text: string) {
  return {
    text: {
      root: {
        type: 'root',
        version: 1,
        format: '',
        indent: 0,
        direction: 'ltr',
        children: [
          {
            type: 'paragraph',
            version: 1,
            format: '',
            indent: 0,
            direction: 'ltr',
            children: [
              {type: 'text', version: 1, text, format: 0, style: '', mode: 'normal', detail: 0},
            ],
          },
        ],
      },
    },
  };
}

async function parse<T>(
  response: APIResponse,
  description: string,
  acceptedStatuses: number[] = []
): Promise<T> {
  if (acceptedStatuses.includes(response.status())) {
    return undefined as T;
  }
  if (!response.ok()) {
    throw new Error(`${description} failed: ${response.status()} ${await response.text()}`);
  }
  const body = await response.text();
  return (body ? JSON.parse(body) : undefined) as T;
}
