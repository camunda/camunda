/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useProcessDefinitionsSearch} from 'modules/queries/processDefinitions/useProcessDefinitionsSearch';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {getProcessDefinitionName} from './processDefinitions';

/**
 * Returns a process definition that can be used as the initial migration target.
 * It is the latest version of the source definition that is not the same version.
 */
function useInitialMigrationTargetProcessDefinition() {
  const {sourceProcessDefinition} = processInstanceMigrationStore.state;

  return useProcessDefinitionsSearch({
    enabled: !!sourceProcessDefinition,
    staleTime: 60_000,
    payload: {
      filter: {
        processDefinitionId: sourceProcessDefinition?.processDefinitionId,
        tenantId: sourceProcessDefinition?.tenantId,
      },
      sort: [{field: 'version', order: 'desc'}],
    },
    select: (definitions) => {
      if (!sourceProcessDefinition) {
        return undefined;
      }
      const sourceKey = sourceProcessDefinition.processDefinitionKey;
      return definitions.find((d) => d.processDefinitionKey !== sourceKey);
    },
  });
}

/**
 * Returns sorted process-definitions that are selectable as a migration target.
 * For the source definition, the next best available version is available
 * or completely excluded.
 */
function useAvailableMigrationTargetProcessDefinitions() {
  const {sourceProcessDefinition} = processInstanceMigrationStore.state;
  const {data: initialMigrationTarget} =
    useInitialMigrationTargetProcessDefinition();

  const {data: otherDefinitions} = useProcessDefinitionsSearch({
    enabled: !!sourceProcessDefinition,
    staleTime: 60_000,
    payload: {
      filter: {
        isLatestVersion: true,
        tenantId: sourceProcessDefinition?.tenantId,
        processDefinitionId: {
          $neq: sourceProcessDefinition?.processDefinitionId,
        },
      },
    },
  });

  return useMemo(() => {
    if (!otherDefinitions) {
      return initialMigrationTarget ? [initialMigrationTarget] : [];
    }
    return otherDefinitions
      .concat(initialMigrationTarget ?? [])
      .sort((d1, d2) => {
        const d1Name = getProcessDefinitionName(d1);
        const d2Name = getProcessDefinitionName(d2);
        return d1Name.localeCompare(d2Name);
      });
  }, [initialMigrationTarget, otherDefinitions]);
}

/**
 * Returns definitions of all available versions for the selected migration target.
 * The source definition version is excluded from the result list.
 */
function useAvailableMigrationTargetProcessDefinitionVersions() {
  const {targetProcessDefinition, sourceProcessDefinition} =
    processInstanceMigrationStore.state;

  return useProcessDefinitionsSearch({
    enabled: !!targetProcessDefinition && !!sourceProcessDefinition,
    staleTime: 60_000,
    payload: {
      filter: {
        processDefinitionId: targetProcessDefinition?.processDefinitionId,
        tenantId: targetProcessDefinition?.tenantId,
      },
      sort: [{field: 'version', order: 'desc'}],
    },
    select: (definitions) => {
      if (!sourceProcessDefinition) {
        return [];
      }
      const sourceKey = sourceProcessDefinition.processDefinitionKey;
      return definitions.filter((d) => d.processDefinitionKey !== sourceKey);
    },
  });
}

export {
  useAvailableMigrationTargetProcessDefinitions,
  useAvailableMigrationTargetProcessDefinitionVersions,
  useInitialMigrationTargetProcessDefinition,
};
