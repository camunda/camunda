/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '@playwright/test';
import {createInstances, deploy} from '../../../../utils/zeebeClient';
import {
  activateJobToObtainAValidJobKey,
  completeUserTask,
  findUserTask,
  searchActiveElementInstance,
  searchElementInstanceByElementIdAndState,
} from '@requestHelpers';
import {assertStatusCode, buildUrl, jsonHeaders} from '../../../../utils/http';
import {
  completeJob,
  searchJobKey,
} from '../../../../utils/requestHelpers/job-requestHelpers';

test.describe('Element Instance Update API', () => {
  const state: Record<string, string> = {};

  test.beforeAll(async () => {
    await deploy(['./resources/element_instance_get_update_tests.bpmn']);
    await createInstances('element_instance_get_update_tests', 1, 1).then(
      (instances) => {
        state.processInstanceKey = instances[0].processInstanceKey;
      },
    );
    await createInstances('element_instance_get_update_tests', 1, 1, {
      should_do_extra_work: true,
    }).then((instances) => {
      state.secondProcessInstanceKey = instances[0].processInstanceKey;
    });
  });

  test('Update Element Instance - so that process ends with the "end without extra" end event', async ({
    request,
  }) => {
    await test.step('Find element instance key of active element', async () => {
      state.elementInstanceKey = await searchActiveElementInstance(
        request,
        state.processInstanceKey,
      );
    });

    await test.step('Update element instance', async () => {
      const res = await request.put(
        buildUrl(
          '/element-instances/' + state.elementInstanceKey + '/variables',
        ),
        {
          headers: jsonHeaders(),
          data: {
            variables: {
              should_do_extra_work: false,
            },
          },
        },
      );

      await assertStatusCode(res, 204);
    });

    await test.step('Complete User Task and Job', async () => {
      await findUserTask(request, state.processInstanceKey, 'CREATED').then(
        async (userTaskKey) => {
          await completeUserTask(request, userTaskKey);
        },
      );

      await activateJobToObtainAValidJobKey(request, 'work').then(
        async (jobKey) => {
          await completeJob(request, jobKey);
        },
      );
    });

    await test.step('Verify process instance has ended with end event "end without extra"', async () => {
      await searchElementInstanceByElementIdAndState(
        request,
        state.processInstanceKey,
        'end_sub_without_extra',
        'COMPLETED',
      );
    });
  });

  test('Update Element Instance - local update overrides global variable', async ({
    request,
  }) => {
    await test.step('Complete User Task', async () => {
      await findUserTask(
        request,
        state.secondProcessInstanceKey,
        'CREATED',
      ).then(async (userTaskKey) => {
        await completeUserTask(request, userTaskKey);
      });
    });

    await test.step('Find element instance key of active element', async () => {
      console.log('Local - Find element instance key of active element');
      state.secondElementInstanceKey =
        await searchElementInstanceByElementIdAndState(
          request,
          state.secondProcessInstanceKey,
          'Activity_0shsai2',
          'ACTIVE',
        );
    });

    await test.step('Update local element instance', async () => {
      const res = await request.put(
        buildUrl(
          '/element-instances/' + state.secondElementInstanceKey + '/variables',
        ),
        {
          headers: jsonHeaders(),
          data: {
            local: true,
            variables: {
              should_do_extra_work: false,
            },
          },
        },
      );

      await assertStatusCode(res, 204);
    });

    await test.step('Complete Job', async () => {
      await searchJobKey(request, state.secondProcessInstanceKey).then(
        async (jobKey) => {
          await completeJob(request, jobKey);
        },
      );
    });

    await test.step('Verify process instance has ended with end event "end without extra"', async () => {
      await searchElementInstanceByElementIdAndState(
        request,
        state.secondProcessInstanceKey,
        'end_sub_without_extra',
        'COMPLETED',
      );
    });
  });
});
