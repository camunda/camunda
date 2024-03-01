/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get, post, put} from 'request';

import {User} from 'components';
export interface ExternalSource {
  type: 'external';
  configuration: {includeAllGroups: boolean; group: string | null};
}

export interface CamundaSource {
  type: 'camunda';
  configuration: {
    eventScope: string[];
    processDefinitionKey: string;
    processDefinitionName: string;
    versions: string[];
    tenants: (string | null)[];
    tracedByBusinessKey: boolean;
    traceVariable: string | null;
  };
}

export type Source = CamundaSource | ExternalSource;

export async function getUsers(id: string): Promise<User[]> {
  const response = await get(`api/eventBasedProcess/${id}/role`);
  return await response.json();
}

export async function updateUsers(id: string, newUsers: User[]) {
  return await put(`api/eventBasedProcess/${id}/role`, newUsers);
}

export async function publish(id: string) {
  return await post(`api/eventBasedProcess/${id}/_publish`);
}

export async function createProcess(payload: {
  eventSources: Source[];
  autogenerate: boolean;
}): Promise<string> {
  const response = await post('api/eventBasedProcess', payload);
  const json = await response.json();

  return json.id;
}

export async function loadExternalGroups(query: {
  searchTerm: string;
  limit: number;
}): Promise<string[]> {
  const response = await get('api/event/groups', query);

  return await response.json();
}
