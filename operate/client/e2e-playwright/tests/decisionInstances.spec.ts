/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setup} from './decisionInstances.mocks';
import {test} from '../test-fixtures';
import {SETUP_WAITING_TIME} from './constants';
import {expect} from '@playwright/test';
import {config} from '../config';

test.describe('Decision Instances', () => {
  let initialData: Awaited<ReturnType<typeof setup>>;

  test.beforeAll(async ({request}) => {
    test.setTimeout(SETUP_WAITING_TIME);
    initialData = await setup();
    const {decisions} = initialData;

    await Promise.all(
      decisions.map(
        async (decision) =>
          await expect
            .poll(
              async () => {
                const response = await request.get(
                  `${config.endpoint}/v1/decision-definitions/${decision.key}`,
                );

                return response.status();
              },
              {timeout: SETUP_WAITING_TIME},
            )
            .toBe(200),
      ),
    );
  });

  test('Switch between Decision versions', async ({decisionsPage}) => {
    const {decisions} = initialData;
    const [
      decision1Version1,
      decision2Version1,
      decision1Version2,
      decision2Version2,
    ] = decisions;

    await decisionsPage.navigateToDecisions({
      searchParams: {
        evaluated: 'true',
        failed: 'true',
      },
    });

    await decisionsPage.selectDecision('Decision 1');
    await decisionsPage.selectVersion(decision1Version1!.version);
    await expect(
      decisionsPage.decisionViewer.getByText('Decision 1'),
    ).toBeVisible();
    await expect(
      decisionsPage.decisionViewer.getByText('Version 1'),
    ).toBeVisible();

    await decisionsPage.selectVersion(decision1Version2!.version);
    await expect(
      decisionsPage.decisionViewer.getByText('Decision 1'),
    ).toBeVisible();
    await expect(
      decisionsPage.decisionViewer.getByText('Version 2'),
    ).toBeVisible();

    await decisionsPage.clearComboBox();
    await decisionsPage.selectDecision('Decision 2');
    await expect(
      decisionsPage.decisionViewer.getByText('Decision 2'),
    ).toBeVisible();

    await decisionsPage.selectVersion(decision2Version1!.version);
    await expect(
      decisionsPage.decisionViewer.getByText('Decision 2'),
    ).toBeVisible();
    await expect(
      decisionsPage.decisionViewer.getByText('Version 1'),
    ).toBeVisible();

    await decisionsPage.selectVersion(decision2Version2!.version);

    await expect(
      decisionsPage.decisionViewer.getByText('Decision 2'),
    ).toBeVisible();
    await expect(
      decisionsPage.decisionViewer.getByText('Version 2'),
    ).toBeVisible();
  });
});
