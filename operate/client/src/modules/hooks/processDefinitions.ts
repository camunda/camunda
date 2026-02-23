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
import {parseProcessDefinitionsSearchFilter} from 'modules/utils/filter/v2/processDefinitionsSearchFilter';
import {useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';

interface ProcessDefinitionWithIdentifier extends ProcessDefinition {
  /** A `definitionId`##`tenantId` tuple that is almost unique but not unique across versions. */
  identifier: string;
  /** A computed label for the definition. Usually the `name`, but uses the `processDefinitionId` as fallback. */
  label: string;
}

type ProcessDefinitionSelection =
  | {kind: 'no-match'}
  | {kind: 'single-version'; definition: ProcessDefinition}
  | {
      kind: 'all-versions';
      definition: Pick<ProcessDefinition, 'name' | 'processDefinitionId'>;
    };

function getDefinitionIdentifier(definitionId: string, tenantId?: string) {
  return `${definitionId}##${tenantId ?? DEFAULT_TENANT}`;
}

function getProcessDefinitionName(
  definition: Pick<ProcessDefinition, 'name' | 'processDefinitionId'>,
) {
  return definition.name ?? definition.processDefinitionId;
}

function splitDefinitionIdentifier(identifier?: string) {
  const [definitionId, tenantId] = identifier?.split('##') ?? [];
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
          label: getProcessDefinitionName(d),
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

function useProcessDefinitionSelection() {
  const filters = useProcessDefinitionsSearchFilter();

  return useProcessDefinitionsSearch<ProcessDefinitionSelection>({
    enabled: !!filters.processDefinitionId,
    payload: {
      filter: {
        processDefinitionId: filters.processDefinitionId,
        version: filters.version,
        tenantId: filters.tenantId,
      },
      sort: [{field: 'version', order: 'desc'}],
    },
    select: (definitions) => {
      const definition = definitions.at(0);
      switch (true) {
        case !definition:
          return {kind: 'no-match'};
        case filters.version === undefined:
          return {
            kind: 'all-versions',
            definition: {
              name: definition.name,
              processDefinitionId: definition.processDefinitionId,
            },
          };
        default:
          return {kind: 'single-version', definition};
      }
    },
  });
}

function useSelectedProcessDefinition() {
  const filters = useProcessDefinitionsSearchFilter();
  return useProcessDefinitionsSearch<ProcessDefinition | undefined>({
    enabled: !!filters.processDefinitionId && !!filters.version,
    payload: {
      filter: {
        processDefinitionId: filters.processDefinitionId,
        version: filters.version,
        tenantId: filters.tenantId,
      },
      sort: [{field: 'version', order: 'desc'}],
    },
    select: (definitions) => {
      if (definitions.length === 1) {
        return definitions[0];
      }
    },
  });
}

function useProcessDefinitionNames(tenantId?: string) {
  return useProcessDefinitionsSearch({
    staleTime: 120_000,
    payload: {filter: {tenantId}},
    select: (definitions) => {
      return definitions.reduce<{[processDefinitionKey: string]: string}>(
        (map, def) => {
          map[def.processDefinitionKey] = getProcessDefinitionName(def);
          return map;
        },
        {},
      );
    },
  });
}

function useProcessDefinitionsSearchFilter() {
  const [searchParams] = useSearchParams();
  return useMemo(
    () => parseProcessDefinitionsSearchFilter(searchParams),
    [searchParams],
  );
}

export {
  type ProcessDefinitionSelection,
  getDefinitionIdentifier,
  getProcessDefinitionName,
  splitDefinitionIdentifier,
  useProcessDefinitions,
  useProcessDefinitionVersions,
  useProcessDefinitionSelection,
  useSelectedProcessDefinition,
  useProcessDefinitionNames,
};
