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
} from '../../../../utils/zeebeClient';
import {
  getAllProcessInstanceVariables,
  getLocalScopeVariables,
  getRootScopeVariables,
  getVariablesByName,
} from '@requestHelpers';

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

test.describe.parallel('Scope-Aware Variable Export — Default Config', () => {
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
      const instance = await createSingleInstance(PROCESS_ID, 1, ROOT_VARS);
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, {}, async (job) => {
        await setVariables(String(job.elementInstanceKey), LOCAL_VARS, true);
        return job.complete({});
      });

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

    test('should export all variables when exportRootVariables=true and exportLocalVariables=true', async ({
      request,
    }) => {
      // given
      const instance = await createSingleInstance(PROCESS_ID, 1, ROOT_VARS);
      const processInstanceKey = String(instance.processInstanceKey);
      processInstanceKeys.push(processInstanceKey);

      const worker = createWorker(WORKER_TYPE, false, {}, async (job) => {
        await setVariables(String(job.elementInstanceKey), LOCAL_VARS, true);
        return job.complete({});
      });
      const vars = await getAllProcessInstanceVariables(
        request,
        processInstanceKey,
        4,
      );
      await worker.close();

      const rootVars = getRootScopeVariables(vars);
      const localVars = getLocalScopeVariables(vars);
      expect(rootVars.map((v) => v.name)).toContain('rootVar1');
      expect(rootVars.map((v) => v.name)).toContain('rootVar2');
      expect(localVars.map((v) => v.name)).toContain('localVar1');
      expect(localVars.map((v) => v.name)).toContain('localVar2');
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

      const worker = createWorker(WORKER_TYPE, false, {}, async (job) => {
        await setVariables(
          String(job.elementInstanceKey),
          {localOnlyVar: 'localScopeValue'},
          true,
        );
        return job.complete({});
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
        4,
      );
      await worker.close();

      const loopItemVars = getVariablesByName(vars, 'loopItem');
      expect(loopItemVars.length).toBeGreaterThanOrEqual(1);
      for (const v of loopItemVars) {
        expect(v.scopeKey).not.toBe(v.processInstanceKey);
      }
    });

    test('should propagate call activity child variables to root scope in the parent process', async ({
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
        expect(v.scopeKey).toBe(v.processInstanceKey);
      }
    });
  });
});