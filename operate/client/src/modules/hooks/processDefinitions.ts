/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.8';
import {DEFAULT_TENANT} from 'modules/constants';
import {useProcessDefinitionsSearch} from 'modules/queries/processDefinitions/useProcessDefinitionsSearch';

interface ProcessDefinitionWithIdentifier extends ProcessDefinition {
  /** A `definitionId`--`tenantId` tuple that is almost unique but not unique across versions. */
  identifier: string;
  /** A computed label for the definition. Usually the `name`, but uses the `processDefinitionId` as fallback. */
  label: string;
}

function getDefinitionIdentifier(definitionId: string, tenantId?: string) {
  return `${definitionId}--${tenantId ?? DEFAULT_TENANT}`;
}

function splitDefinitionIdentifier(identifier?: string) {
  const [definitionId, tenantId] = identifier?.split('--') ?? [];
  return {
    definitionId: definitionId || undefined,
    tenantId: tenantId || undefined,
  };
}

/**
 * Returns sorted process-definitions for an optional `tenantId`.
 * The definitions are deduplicated through the `isLatestVersion` filter.
 */
function useProcessDefinitions(tenantId?: string) {
  return useProcessDefinitionsSearch({
    payload: {filter: {tenantId, isLatestVersion: true}},
    select: (definitions) =>
      definitions
        .map<ProcessDefinitionWithIdentifier>((d) => ({
          ...d,
          label: d.name ?? d.processDefinitionId,
          identifier: getDefinitionIdentifier(
            d.processDefinitionId,
            d.tenantId,
          ),
        }))
        .sort((d1, d2) => d1.label.localeCompare(d2.label)),
  });
}

/**
 * Returns sorted definition versions for a given `processDefinitionId` and `tenantId`.
 * The query is disabled when no `processDefinitionId` is provided. An `'all'` version
 * is already included when needed.
 */
function useProcessDefinitionVersions(
  processDefinitionId?: string,
  tenantId?: string,
) {
  return useProcessDefinitionsSearch({
    enabled: !!processDefinitionId,
    payload: {
      filter: {processDefinitionId, tenantId},
      sort: [{field: 'version', order: 'desc'}],
    },
    select: (definitions) => {
      const versions = definitions.map((definition) => definition.version);
      return versions.length > 1 ? ['all', ...versions] : versions;
    },
  });
}

export {
  getDefinitionIdentifier,
  splitDefinitionIdentifier,
  useProcessDefinitions,
  useProcessDefinitionVersions,
};
