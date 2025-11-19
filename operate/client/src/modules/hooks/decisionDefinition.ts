/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DecisionDefinition} from '@camunda/camunda-api-zod-schemas/8.8';
import {useDecisionInstancesSearchFilter} from './decisionInstancesSearch';
import {useDecisionDefinitionsSearch} from 'modules/queries/decisionDefinitions/useDecisionDefinitionsSearch';
import {DEFAULT_TENANT} from 'modules/constants';

interface DecisionDefinitionWithIdentifier extends DecisionDefinition {
  /** A `definitionId`--`tenantId` tuple that is almost unique but not unique across versions. */
  identifier: string;
}

function getDefinitionIdentifier(definitionId: string, tenantId?: string) {
  return `${definitionId}--${tenantId ?? DEFAULT_TENANT}`;
}

function getDefinitionIdFromIdentifier(
  identifier?: string,
): string | undefined {
  return identifier?.split('--')[0];
}

/**
 * Returns sorted decision-definitions for an optional `tenantId`.
 * The definition are deduplicated for their `decisionDefinitionIds` and `tenantIds`
 * to include only the latest `version`.
 */
function useDecisionDefinitions(tenantId?: string) {
  return useDecisionDefinitionsSearch({
    payload: {
      filter: {tenantId},
      sort: [
        {field: 'name', order: 'asc'},
        {field: 'version', order: 'desc'},
      ],
    },
    select: (definitions) => {
      const uniquifier = new Map<string, DecisionDefinitionWithIdentifier>();
      for (const definition of definitions) {
        const identifier = getDefinitionIdentifier(
          definition.decisionDefinitionId,
          definition.tenantId,
        );
        const seenDefinition = uniquifier.get(identifier);
        if (!seenDefinition || seenDefinition.version < definition.version) {
          uniquifier.set(identifier, {...definition, identifier});
        }
      }
      return Array.from(uniquifier.values());
    },
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
 * The query is disabled when the filter configuration is incomplete.
 */
function useSelectedDecisionDefinition() {
  const filters = useDecisionInstancesSearchFilter();
  const enabled =
    !!filters?.decisionDefinitionId && !!filters.decisionDefinitionVersion;

  return useDecisionDefinitionsSearch({
    enabled,
    payload: {
      filter: {
        decisionDefinitionId: filters?.decisionDefinitionId,
        version: filters?.decisionDefinitionVersion,
        tenantId: filters?.tenantId,
      },
    },
    select: (definitions) => definitions.at(0),
  });
}

export type {DecisionDefinitionWithIdentifier};
export {
  getDefinitionIdentifier,
  getDefinitionIdFromIdentifier,
  useDecisionDefinitionVersions,
  useDecisionDefinitions,
  useSelectedDecisionDefinition,
};
