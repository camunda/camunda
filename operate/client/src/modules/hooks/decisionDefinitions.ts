/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import type {DecisionDefinition} from '@camunda/camunda-api-zod-schemas/8.8';
import {parseDecisionDefinitionsSearchFilter} from 'modules/utils/filter/decisionsFilter';
import {useDecisionDefinitionsSearch} from 'modules/queries/decisionDefinitions/useDecisionDefinitionsSearch';
import {DEFAULT_TENANT} from 'modules/constants';

interface DecisionDefinitionWithIdentifier extends DecisionDefinition {
  /** A `definitionId`##`tenantId` tuple that is almost unique but not unique across versions. */
  identifier: string;
}

type DecisionDefinitionSelection =
  | {kind: 'no-match'}
  | {kind: 'single-version'; definition: DecisionDefinition}
  | {
      kind: 'all-versions';
      definition: Pick<DecisionDefinition, 'name' | 'decisionDefinitionId'>;
    };

function getDefinitionIdentifier(definitionId: string, tenantId?: string) {
  return `${definitionId}##${tenantId ?? DEFAULT_TENANT}`;
}

function getDefinitionIdFromIdentifier(
  identifier?: string,
): string | undefined {
  return identifier?.split('##')[0];
}

/**
 * Returns sorted decision-definitions for an optional `tenantId`.
 * The definitions are deduplicated through the `isLatestVersion` filter.
 */
function useDecisionDefinitions(tenantId?: string) {
  return useDecisionDefinitionsSearch({
    payload: {filter: {tenantId, isLatestVersion: true}},
    select: (definitions) =>
      definitions
        .map<DecisionDefinitionWithIdentifier>((definition) => ({
          ...definition,
          identifier: getDefinitionIdentifier(
            definition.decisionDefinitionId,
            definition.tenantId,
          ),
        }))
        .sort((d1, d2) => d1.name.localeCompare(d2.name)),
  });
}

/**
 * Returns sorted definition versions for a given `decisionDefinitionId` and `tenantId`.
 * The query is disabled when no `decisionDefinitionId` is provided. An `'all'` version
 * is already included when needed.
 */
function useDecisionDefinitionVersions(
  decisionDefinitionId?: string,
  tenantId?: string,
) {
  return useDecisionDefinitionsSearch({
    enabled: !!decisionDefinitionId,
    payload: {
      filter: {decisionDefinitionId, tenantId},
      sort: [{field: 'version', order: 'desc'}],
    },
    select: (definitions) => {
      const versions = definitions.map((d) => d.version);
      return versions.length > 1 ? ['all', ...versions] : versions;
    },
  });
}

/**
 * Returns the decision-definition that matches the selected decisions filters.
 * The query is disabled when no `decisionDefinitionId` is provided.
 *
 * If no version filter (or 'all') is set, the selection state will be `all-versions`
 * with reduced definition information. In this case, the query cache is shared with
 * {@link useDecisionDefinitionVersions} by providing the same query payload.
 */
function useDecisionDefinitionSelection() {
  const filters = useDecisionDefinitionsSearchFilter();

  return useDecisionDefinitionsSearch<DecisionDefinitionSelection>({
    enabled: !!filters.decisionDefinitionId,
    payload: {
      filter: {
        decisionDefinitionId: filters.decisionDefinitionId,
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
        case filters?.version === undefined:
          return {
            kind: 'all-versions',
            definition: {
              name: definition.name,
              decisionDefinitionId: definition.decisionDefinitionId,
            },
          };
        default:
          return {kind: 'single-version', definition};
      }
    },
  });
}

function useDecisionDefinitionsSearchFilter() {
  const [searchParams] = useSearchParams();
  return useMemo(
    () => parseDecisionDefinitionsSearchFilter(searchParams),
    [searchParams],
  );
}

export {
  getDefinitionIdentifier,
  getDefinitionIdFromIdentifier,
  useDecisionDefinitionVersions,
  useDecisionDefinitions,
  useDecisionDefinitionSelection,
};
