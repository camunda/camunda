/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {CREATE_CLUSTER_VARIABLE_WITH_METADATA} from '../../../../utils/beans/requestBeans';
import {
  generateUniqueId,
  generateUniqueProcessDefinitionId,
} from '../../../../utils/constants';
import {
  createGlobalClusterVariable,
  createTenantClusterVariable,
  activateFirstJobVariables,
  assertNoMetadataLeak,
} from '@requestHelpers';
import {searchVariableByNameAndProcessInstanceKey} from '../../../../utils/requestHelpers/variable-requestHelpers';
import {
  cleanupGlobalClusterVariables,
  cleanupTenantClusterVariables,
} from '../../../../utils/clusterVariablesCleanup';
import {
  deployWithSubstitutions,
  createSingleInstance,
} from '../../../../utils/zeebeClient';

const ISOLATION_RESOURCE =
  './resources/cluster_variable_metadata_isolation.bpmn';

// Substituted per run so parallel tests never share a definition, job type or variable.
const PROCESS_ID_PLACEHOLDER = 'clusterVariableMetadataIsolation';
const JOB_TYPE_PLACEHOLDER = 'cvMetadataIsolationWorker';
const VARIABLE_NAME_PLACEHOLDER = 'cvMetadataIsolationVar';

const METADATA = {
  category: 'TAX_RATE',
  region: 'EU',
  year: 2026,
};

async function deployAndStartIsolationProcess(clusterVariableName: string) {
  const processDefinitionId = generateUniqueProcessDefinitionId();
  const jobType = `cvIsolation${generateUniqueId()}`;

  await deployWithSubstitutions(ISOLATION_RESOURCE, {
    [PROCESS_ID_PLACEHOLDER]: processDefinitionId,
    [JOB_TYPE_PLACEHOLDER]: jobType,
    [VARIABLE_NAME_PLACEHOLDER]: clusterVariableName,
  });

  const instance = await createSingleInstance(processDefinitionId, 1);

  return {jobType, processInstanceKey: String(instance.processInstanceKey)};
}

test.describe.parallel('Cluster Variable API Tests - FEEL Isolation', () => {
  const state: Record<string, unknown> = {};
  const createdGlobalNames: string[] = [];
  const createdTenantVariables: {tenantId: string; name: string}[] = [];

  test.afterAll(async ({request}) => {
    await cleanupGlobalClusterVariables(request, createdGlobalNames);
    await cleanupTenantClusterVariables(request, createdTenantVariables);
  });

  test('Metadata Is Not Present In Job Variables Resolved Via Env Namespace', async ({
    request,
  }) => {
    const variable = CREATE_CLUSTER_VARIABLE_WITH_METADATA(METADATA);
    await createGlobalClusterVariable(request, state, 'envIsolation', variable);
    createdGlobalNames.push(variable.name);

    await test.step('Metadata is stored on the variable', async () => {
      expect(state['envIsolationMetadata']).toEqual(METADATA);
    });

    const {jobType, processInstanceKey} = await deployAndStartIsolationProcess(
      variable.name,
    );

    await test.step('Activated job sees only the value contents', async () => {
      const jobVariables = await activateFirstJobVariables(
        request,
        jobType,
        processInstanceKey,
      );
      assertNoMetadataLeak(
        jobVariables['mappedValue'],
        variable.value,
        jobVariables,
        METADATA,
      );
    });
  });

  test('Metadata Is Not Present In Process Instance Variables', async ({
    request,
  }) => {
    const variable = CREATE_CLUSTER_VARIABLE_WITH_METADATA(METADATA);
    await createGlobalClusterVariable(request, state, 'piIsolation', variable);
    createdGlobalNames.push(variable.name);

    await test.step('Metadata is stored on the variable', async () => {
      expect(state['piIsolationMetadata']).toEqual(METADATA);
    });

    const {processInstanceKey} = await deployAndStartIsolationProcess(
      variable.name,
    );

    await test.step('Stored process variable holds only the value contents', async () => {
      const stored = await searchVariableByNameAndProcessInstanceKey(request, {
        processInstanceKey,
        name: 'mappedValue',
      });

      // Search returns variable values as JSON strings. Swap in the parsed value so
      // the whole response is walked and the value is walked structurally, not as
      // one opaque string leaf.
      const parsed = JSON.parse(stored.value);
      assertNoMetadataLeak(
        parsed,
        variable.value,
        {...stored, value: parsed},
        METADATA,
      );
    });
  });

  test('Metadata Is Not Present In FEEL Resolved Value For Tenant Scoped Variable', async ({
    request,
  }) => {
    // Resolved via env against the default tenant, so the test does not depend on
    // multiTenancy.checksEnabled being on.
    const tenantId = '<default>';
    const variable = CREATE_CLUSTER_VARIABLE_WITH_METADATA(METADATA);
    await createTenantClusterVariable(
      request,
      state,
      'tenantIsolation',
      tenantId,
      variable,
    );
    createdTenantVariables.push({tenantId, name: variable.name});

    await test.step('Metadata is stored on the tenant scoped variable', async () => {
      expect(state['tenantIsolationMetadata']).toEqual(METADATA);
    });

    const {jobType, processInstanceKey} = await deployAndStartIsolationProcess(
      variable.name,
    );

    await test.step('Activated job sees only the value contents', async () => {
      const jobVariables = await activateFirstJobVariables(
        request,
        jobType,
        processInstanceKey,
      );
      assertNoMetadataLeak(
        jobVariables['mappedValue'],
        variable.value,
        jobVariables,
        METADATA,
      );
    });
  });
});
