/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {searchProcessDefinitions} from '../v2/processDefinitions/searchProcessDefinitions';
import {type QueryProcessDefinitionsResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {DEFAULT_TENANT} from 'modules/constants';

type ProcessVersionDto = {
  bpmnProcessId: string;
  id: string;
  name: string;
  version: number;
  versionTag: string | null;
};

type ProcessDto = {
  bpmnProcessId: string;
  name: string | null;
  processes: ProcessVersionDto[];
  tenantId: string;
};

const fetchGroupedProcesses = async (tenantId?: string) => {
  const {response, error} = await searchProcessDefinitions({
    filter: {tenantId},
  });
  if (error) {
    return {isSuccess: false} as const;
  }

  if (response.page.totalItems <= response.items.length) {
    return {
      isSuccess: true,
      data: mapProcessDefinitionsToProcessesV1(response.items),
    } as const;
  }

  const remaining = await searchProcessDefinitions({
    filter: {tenantId},
    page: {
      from: response.items.length,
      limit: response.page.totalItems,
    },
  });
  if (remaining.error) {
    return {isSuccess: false} as const;
  }

  const allDefinitions = response.items.concat(remaining.response.items);
  return {
    isSuccess: true,
    data: mapProcessDefinitionsToProcessesV1(allDefinitions),
  } as const;
};

function mapProcessDefinitionsToProcessesV1(
  definitions: QueryProcessDefinitionsResponseBody['items'],
): ProcessDto[] {
  const groups = new Map<string, (ProcessVersionDto & {tenantId: string})[]>();
  for (const definition of definitions) {
    const identifier = `${definition.processDefinitionId}--${definition.tenantId ?? DEFAULT_TENANT}`;
    const bucket = groups.get(identifier) ?? [];
    bucket.push({
      bpmnProcessId: definition.processDefinitionId,
      id: definition.processDefinitionKey,
      name: definition.name ?? definition.processDefinitionId,
      version: definition.version,
      versionTag: definition.versionTag ?? null,
      tenantId: definition.tenantId ?? DEFAULT_TENANT,
    });
    groups.set(identifier, bucket);
  }

  const processes: ProcessDto[] = [];
  for (const bucket of groups.values()) {
    const latestVersion = bucket.reduce((latest, dto) =>
      latest.version > dto.version ? latest : dto,
    );
    processes.push({
      bpmnProcessId: latestVersion.bpmnProcessId,
      name:
        latestVersion.name === latestVersion.bpmnProcessId
          ? null
          : latestVersion.name,
      processes: bucket.map((versionDefinition) => ({
        bpmnProcessId: versionDefinition.bpmnProcessId,
        id: versionDefinition.id,
        name: versionDefinition.name,
        version: versionDefinition.version,
        versionTag: versionDefinition.versionTag,
      })),
      tenantId: latestVersion.tenantId,
    });
  }
  return processes;
}

export {fetchGroupedProcesses};
export type {ProcessDto, ProcessVersionDto};
