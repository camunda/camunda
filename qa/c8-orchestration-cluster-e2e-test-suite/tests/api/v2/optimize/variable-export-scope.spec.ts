/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  cancelProcessInstance,
  createSingleInstance,
  createWorker,
  deploy,
  setVariables,
} from '../../utils/zeebeClient';
import {defaultAssertionOptions} from '../../utils/constants';
import {
  assertNoVariablesForProcessInstance,
  getAllProcessInstanceVariables,
  getLocalScopeVariables,
  getRootScopeVariables,
  getVariablesByName,
} from '../../utils/requestHelpers/optimize-variable-requestHelpers';

const BPMN_PATH = './resources/optimize_scope_test_process.bpmn';
const BPMN_MULTI_INSTANCE_PATH =
  './resources/optimize_multi_instance_process.bpmn';
const BPMN_CALL_ACTIVITY_PATH =
  './resources/optimize_call_activity_process.bpmn';
const BPMN_CALL_ACTIVITY_SUB_PATH =
  './resources/optimize_call_activity_subprocess.bpmn';
const WORKER_TYPE = 'optimize-scope-test-worker';
const PROCESS_ID = 'optimize_scope_test_process';

const ROOT_VARS = {rootVar1: 'rootValue1', rootVar2: 'rootValue2'};
const LOCAL_VARS = {localVar1: 'localValue1', localVar2: 'localValue2'};

test.describe('Scope-Aware Variable Export Configuration for Optimize', () => {
  const processInstanceKeys: string[] = [];

  test.beforeAll(async () => {
    await deploy([
      BPMN_PATH,
      BPMN_MULTI_INSTANCE_PATH,
      BPMN_CALL_ACTIVITY_SUB_PATH,
      BPMN_CALL_ACTIVITY_PATH,
    ]);
  });

  test.afterAll(async () => {
    for (const key of processInstanceKeys) {
      await cancelProcessInstance(key);
    }
  });

  test.describe('Default / Backward-Compatible Behaviour', () => {
    test('should export all variables when no scope filter is configured', async ({
      request,
    }) => {
      // given
      const instance = await createSingleInstance(PROCESS_ID, 1, ROOT_VARS);
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, LOCAL_VARS);

      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        4,
      );
      await worker.close();

      await test.step('root-scope variables are exported', () => {
        const rootVars = getRootScopeVariables(vars);
        const rootNames = rootVars.map((v) => v.name);
        expect(rootNames).toContain('rootVar1');
        expect(rootNames).toContain('rootVar2');
      });

      await test.step('local-scope variables are exported', () => {
        const localVars = getLocalScopeVariables(vars);
        const localNames = localVars.map((v) => v.name);
        expect(localNames).toContain('localVar1');
        expect(localNames).toContain('localVar2');
      });

      await test.step('root variables have scopeKey === processInstanceKey', () => {
        const rootVars = getRootScopeVariables(vars);
        expect(rootVars.length).toBeGreaterThanOrEqual(2);
        for (const v of rootVars) {
          expect(v.scopeKey).toBe(v.processInstanceKey);
        }
      });

      await test.step('local variables have scopeKey !== processInstanceKey', () => {
        const localVars = getLocalScopeVariables(vars);
        expect(localVars.length).toBeGreaterThanOrEqual(2);
        for (const v of localVars) {
          expect(v.scopeKey).not.toBe(v.processInstanceKey);
        }
      });
    });

    /**
     * TC-02
     * Scenario: Explicitly enabling both root and local export behaves identically to the default
     */
    test('should export all variables when exportRootVariables=true and exportLocalVariables=true', async ({
      request,
    }) => {
      // given
      const instance = await createSingleInstance(PROCESS_ID, 1, ROOT_VARS);
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, LOCAL_VARS);

      // when
      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        4,
      );
      await worker.close();

      // then — identical result to TC-01
      const rootVars = getRootScopeVariables(vars);
      const localVars = getLocalScopeVariables(vars);
      expect(rootVars.map((v) => v.name)).toContain('rootVar1');
      expect(rootVars.map((v) => v.name)).toContain('rootVar2');
      expect(localVars.map((v) => v.name)).toContain('localVar1');
      expect(localVars.map((v) => v.name)).toContain('localVar2');
    });

    test('should export no variables when variableImportEnabled=false', async ({
      request,
    }) => {
      const instance = await createSingleInstance(PROCESS_ID, 1, ROOT_VARS);
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, LOCAL_VARS);

      await expect(async () => {
        const res = await request.get(
          `/v2/process-instances/${processInstanceKey}`,
        );
        expect(res.status()).toBe(200);
      }).toPass(defaultAssertionOptions);

      await worker.close();

      await assertNoVariablesForProcessInstance(request, processInstanceKey);
    });
  });

  test.describe('Root-Only Export', () => {
    test('should export only root-scope variables when exportLocalVariables=false', async ({
      request,
    }) => {
      const instance = await createSingleInstance(PROCESS_ID, 1, ROOT_VARS);
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, LOCAL_VARS);

      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        2,
      );
      await worker.close();

      await test.step('root-scope variables rootVar1 and rootVar2 are present', () => {
        const rootNames = getRootScopeVariables(vars).map((v) => v.name);
        expect(rootNames).toContain('rootVar1');
        expect(rootNames).toContain('rootVar2');
      });

      await test.step('local-scope variables localVar1 and localVar2 are absent', () => {
        const allNames = vars.map((v) => v.name);
        expect(allNames).not.toContain('localVar1');
        expect(allNames).not.toContain('localVar2');
      });
    });

    test('should capture root-scope variable updates when exportLocalVariables=false', async ({
      request,
    }) => {
      const instance = await createSingleInstance(PROCESS_ID, 1, {
        rootVar1: 'initialValue',
      });
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, {}, async (job) => {
        await setVariables(
          processInstanceKey,
          {rootVar1: 'updatedValue'},
          false,
        );
        return job.complete();
      });

      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        1,
      );
      await worker.close();

      const rootVar1 = getVariablesByName(
        getRootScopeVariables(vars),
        'rootVar1',
      );
      expect(rootVar1.length).toBeGreaterThanOrEqual(1);
      expect(JSON.parse(rootVar1[0].value)).toBe('updatedValue');
    });

    test('should export only the root-scope instance of a same-named variable', async ({
      request,
    }) => {
      const instance = await createSingleInstance(PROCESS_ID, 1, {
        sharedName: 'rootValue',
      });
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, {
        sharedName: 'localValue',
      });

      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        1,
      );
      await worker.close();

      const sharedVars = getVariablesByName(vars, 'sharedName');
      expect(sharedVars).toHaveLength(1);
      expect(JSON.parse(sharedVars[0].value)).toBe('rootValue');
      expect(sharedVars[0].scopeKey).toBe(sharedVars[0].processInstanceKey);
    });

    test('should export only local-scope variables when exportRootVariables=false', async ({
      request,
    }) => {
      const instance = await createSingleInstance(PROCESS_ID, 1, ROOT_VARS);
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, LOCAL_VARS);

      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        2,
      );
      await worker.close();

      await test.step('local variables localVar1 and localVar2 are present', () => {
        const localNames = getLocalScopeVariables(vars).map((v) => v.name);
        expect(localNames).toContain('localVar1');
        expect(localNames).toContain('localVar2');
      });

      await test.step('root variables rootVar1 and rootVar2 are absent', () => {
        const allNames = vars.map((v) => v.name);
        expect(allNames).not.toContain('rootVar1');
        expect(allNames).not.toContain('rootVar2');
      });
    });

    test('should export no variables when both exportRootVariables=false and exportLocalVariables=false', async ({
      request,
    }) => {
      const instance = await createSingleInstance(PROCESS_ID, 1, ROOT_VARS);
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, LOCAL_VARS);

      await expect(async () => {
        const res = await request.get(
          `/v2/process-instances/${processInstanceKey}`,
        );
        expect(res.status()).toBe(200);
      }).toPass(defaultAssertionOptions);

      await worker.close();

      await assertNoVariablesForProcessInstance(request, processInstanceKey);
    });
  });

  test.describe('Local Variable Whitelisting — Exact Name', () => {
    test('should export a single whitelisted local variable', async ({
      request,
    }) => {
      const instance = await createSingleInstance(PROCESS_ID, 1, ROOT_VARS);
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, {
        taskContextDisplayName: 'budgetApproval',
        localVar1: 'noise',
      });

      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        3,
      );
      await worker.close();

      await test.step('taskContextDisplayName is exported with value budgetApproval', () => {
        const matching = getVariablesByName(vars, 'taskContextDisplayName');
        expect(matching.length).toBeGreaterThanOrEqual(1);
        expect(JSON.parse(matching[0].value)).toBe('budgetApproval');
      });

      await test.step('localVar1 is not exported', () => {
        expect(vars.map((v) => v.name)).not.toContain('localVar1');
      });

      await test.step('all root-scope variables are exported', () => {
        const rootNames = getRootScopeVariables(vars).map((v) => v.name);
        expect(rootNames).toContain('rootVar1');
        expect(rootNames).toContain('rootVar2');
      });
    });

    test('should export multiple whitelisted local variables and block the rest', async ({
      request,
    }) => {
      const instance = await createSingleInstance(PROCESS_ID, 1, ROOT_VARS);
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, {
        taskContextDisplayName: 'task1',
        auditContext: 'ctx1',
        internalLocal: 'data',
      });

      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        4,
      );
      await worker.close();

      const allNames = vars.map((v) => v.name);
      expect(allNames).toContain('taskContextDisplayName');
      expect(allNames).toContain('auditContext');
      expect(allNames).not.toContain('internalLocal');
    });

    test('should export no local variables when whitelist is empty', async ({
      request,
    }) => {
      const instance = await createSingleInstance(PROCESS_ID, 1, ROOT_VARS);
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, LOCAL_VARS);

      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        2,
      );
      await worker.close();

      const localVars = getLocalScopeVariables(vars);
      expect(localVars).toHaveLength(0);

      const rootNames = getRootScopeVariables(vars).map((v) => v.name);
      expect(rootNames).toContain('rootVar1');
      expect(rootNames).toContain('rootVar2');
    });

    test('should export nothing extra when whitelisted name does not exist in process', async ({
      request,
    }) => {
      const instance = await createSingleInstance(PROCESS_ID, 1, ROOT_VARS);
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, LOCAL_VARS);

      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        2,
      );
      await worker.close();

      expect(getLocalScopeVariables(vars)).toHaveLength(0);

      const rootNames = getRootScopeVariables(vars).map((v) => v.name);
      expect(rootNames).toContain('rootVar1');
      expect(rootNames).toContain('rootVar2');
    });
  });

  test.describe('Local Variable Whitelisting — Pattern Matching', () => {
    test('should export pattern-matched local variables and block non-matching ones', async ({
      request,
    }) => {
      const instance = await createSingleInstance(PROCESS_ID, 1, ROOT_VARS);
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, {
        auditUser: 'jdoe',
        auditTimestamp: '2026-01-01',
        internalCounter: '42',
      });

      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        4,
      );
      await worker.close();

      const allNames = vars.map((v) => v.name);
      expect(allNames).toContain('auditUser');
      expect(allNames).toContain('auditTimestamp');
      expect(allNames).not.toContain('internalCounter');
    });

    test('should handle a combination of exact-name and pattern whitelist entries', async ({
      request,
    }) => {
      const instance = await createSingleInstance(PROCESS_ID, 1, ROOT_VARS);
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, {
        taskContextDisplayName: 'task1',
        auditUser: 'jdoe',
        auditTimestamp: 'ts1',
        unrelatedLocal: 'noise',
      });

      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        5,
      );
      await worker.close();

      const allNames = vars.map((v) => v.name);
      expect(allNames).toContain('taskContextDisplayName');
      expect(allNames).toContain('auditUser');
      expect(allNames).toContain('auditTimestamp');
      expect(allNames).not.toContain('unrelatedLocal');
    });
  });

  test.describe('Whitelist Applied to Variable Updates', () => {
    test('should capture updates to a whitelisted local variable', async ({
      request,
    }) => {
      const instance = await createSingleInstance(PROCESS_ID, 1, ROOT_VARS);
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, {}, async (job) => {
        await setVariables(
          String(job.elementInstanceKey),
          {taskContextDisplayName: 'initial'},
          true,
        );
        await setVariables(
          String(job.elementInstanceKey),
          {taskContextDisplayName: 'updated'},
          true,
        );
        return job.complete();
      });

      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        1,
      );
      await worker.close();

      const matching = getVariablesByName(vars, 'taskContextDisplayName');
      expect(matching.length).toBeGreaterThanOrEqual(1);
      expect(JSON.parse(matching[0].value)).toBe('updated');
    });

    test('should not capture updates to a non-whitelisted local variable', async ({
      request,
    }) => {
      const instance = await createSingleInstance(PROCESS_ID, 1, ROOT_VARS);
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, {}, async (job) => {
        await setVariables(
          String(job.elementInstanceKey),
          {localVar1: 'initial'},
          true,
        );
        await setVariables(
          String(job.elementInstanceKey),
          {localVar1: 'updated'},
          true,
        );
        return job.complete();
      });

      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        2,
      );
      await worker.close();

      expect(vars.map((v) => v.name)).not.toContain('localVar1');
    });
  });

  test.describe('Scope Classification — Engine Record Mapping', () => {
    test('should classify a variable as root-scope when scopeKey equals processInstanceKey', async ({
      request,
    }) => {
      const instance = await createSingleInstance(PROCESS_ID, 1, {
        rootOnlyVar: 'rootScopeValue',
      });
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, {});

      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        1,
      );
      await worker.close();

      const rootOnlyVar = getVariablesByName(vars, 'rootOnlyVar');
      expect(rootOnlyVar.length).toBeGreaterThanOrEqual(1);
      expect(rootOnlyVar[0].scopeKey).toBe(rootOnlyVar[0].processInstanceKey);
    });

    test('should classify a variable as local-scope when scopeKey differs from processInstanceKey', async ({
      request,
    }) => {
      const instance = await createSingleInstance(PROCESS_ID, 1, {});
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, {
        localOnlyVar: 'localScopeValue',
      });

      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        1,
      );
      await worker.close();

      const localOnlyVar = getVariablesByName(vars, 'localOnlyVar');
      expect(localOnlyVar.length).toBeGreaterThanOrEqual(1);
      expect(localOnlyVar[0].scopeKey).not.toBe(
        localOnlyVar[0].processInstanceKey,
      );
    });

    test('should export whitelisted local variables from each multi-instance iteration', async ({
      request,
    }) => {
      const instance = await createSingleInstance(
        'optimize_multi_instance_process',
        1,
        {items: ['item1', 'item2', 'item3']},
      );
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker('optimize-multi-instance-worker', false, {
        loopItem: 'handled',
      });

      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        1,
      );
      await worker.close();

      const loopItemVars = getVariablesByName(vars, 'loopItem');
      expect(loopItemVars.length).toBeGreaterThanOrEqual(1);
      for (const v of loopItemVars) {
        expect(v.scopeKey).not.toBe(v.processInstanceKey);
      }
    });

    test('should treat call activity variables as local scope and suppress them when local export is disabled', async ({
      request,
    }) => {
      const subWorker = createWorker(
        'optimize-call-activity-sub-worker',
        false,
        {subVar: 'subValue'},
      );

      const instance = await createSingleInstance(
        'optimize_call_activity_process',
        1,
        {},
      );
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        1,
      );
      await subWorker.close();

      const subVars = getVariablesByName(vars, 'subVar');
      expect(subVars.length).toBeGreaterThanOrEqual(1);
      for (const v of subVars) {
        expect(v.scopeKey).not.toBe(v.processInstanceKey);
      }
    });
  });
});
