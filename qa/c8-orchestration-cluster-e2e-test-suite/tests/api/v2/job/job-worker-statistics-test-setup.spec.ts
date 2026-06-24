/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '@playwright/test';
import {createSingleInstance, createWorker, deploy} from 'utils/zeebeClient';

test.describe.parallel('Job Statistics By Worker Setup', () => {
  const expectedJobType = 'incidentGenerator';

  test('Setup - Create jobs with different workers and types', async () => {
    await test.step('Setup - Create process with worker', async () => {
      await deploy(['./resources/incidentGeneratorProcess.bpmn']);

      createWorker(expectedJobType, true, {}, (job) => {
        const BASE_ERROR_MESSAGE =
          'This is an error message for testing purposes. This error message is very long to ensure it is truncated in the UI.';

        if (job.variables.incidentType === 'Incident Type A') {
          return job.fail(`${BASE_ERROR_MESSAGE} Type A`);
        } else {
          return job.fail(`${BASE_ERROR_MESSAGE} Type B`);
        }
      });

      await createSingleInstance('incidentGeneratorProcess', 1, {
        incidentType: 'Incident Type A',
      });
      await createSingleInstance('incidentGeneratorProcess', 1, {
        incidentType: 'Incident Type B',
      });
    });
  });
});
