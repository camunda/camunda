/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setup} from './decisionInstance.mocks';
import {test} from '../test-fixtures';
import {SETUP_WAITING_TIME} from './constants';
import {expect} from '@playwright/test';
import {config} from '../config';
import {zeebeGrpcApi} from '../api/zeebe-grpc';

test.describe('Decision Instance', () => {
  let initialData: Awaited<ReturnType<typeof setup>>;
  let decisionInstanceKey: string;

  test.beforeAll(async ({request}) => {
    test.setTimeout(SETUP_WAITING_TIME);
    initialData = await setup();

    await expect
      .poll(
        async () => {
          const response = await request.get(
            `${config.endpoint}/v1/decision-definitions/${initialData.decision1Key}`,
          );

          return response.status();
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBe(200);

    const evaluationResponse = await zeebeGrpcApi.evaluateDecision({
      decisionKey: initialData.decision1Key!,
      variables: {input: 'foo'},
    });
    //@ts-ignore
    decisionInstanceKey = evaluationResponse.decisionInstanceKey;

    // Wait for decision instances to be available in Operate
    await Promise.all(
      [`${decisionInstanceKey}-1`, `${decisionInstanceKey}-2`].map(
        async (instanceKey) =>
          await expect
            .poll(
              async () => {
                const response = await request.get(
                  `${config.endpoint}/v1/decision-instances/${instanceKey}`,
                );

                return response.status();
              },
              {timeout: SETUP_WAITING_TIME},
            )
            .toBe(200),
      ),
    );
  });

  test('Switching between decisions', async ({decisionInstancePage}) => {
    await decisionInstancePage.navigateToDecisionInstance({
      decisionInstanceKey: `${decisionInstanceKey}-1`,
    });

    const {drdPanel, decisionPanel, inputVariables, outputVariables} =
      decisionInstancePage;

    await expect(drdPanel.getByText('Decision 1')).toBeVisible();
    await expect(drdPanel.getByText('Decision 2')).toBeVisible();
    await expect(decisionPanel.getByText('Decision 1')).toBeVisible();
    await expect(decisionPanel.getByText('Decision 2')).not.toBeVisible();

    /**
     * input variables assertions (Decision 1)
     */
    const inputVariable1Row = inputVariables.getByRole('row').nth(1);
    const inputVariable1Name = inputVariable1Row.getByRole('cell').nth(0);
    const inputVariable1Value = inputVariable1Row.getByRole('cell').nth(1);
    await expect(inputVariables.getByRole('row')).toHaveCount(2);
    await expect(inputVariable1Name).toHaveText('input');
    await expect(inputVariable1Value).toHaveText('"foo"');

    /**
     * output variables assertions (Decision 1)
     */
    const outputVariable1Row = outputVariables.getByRole('row').nth(1);
    const outputVariable2Row = outputVariables.getByRole('row').nth(2);
    const outputVariable1Rule = outputVariable1Row.getByRole('cell').nth(0);
    const outputVariable1Name = outputVariable1Row.getByRole('cell').nth(1);
    const outputVariable1Value = outputVariable1Row.getByRole('cell').nth(2);
    const outputVariable2Rule = outputVariable2Row.getByRole('cell').nth(0);
    const outputVariable2Name = outputVariable2Row.getByRole('cell').nth(1);
    const outputVariable2Value = outputVariable2Row.getByRole('cell').nth(2);
    await expect(outputVariables.getByRole('row')).toHaveCount(3);
    await expect(outputVariable1Rule).toHaveText('1');
    await expect(outputVariable1Name).toHaveText('output');
    await expect(outputVariable1Value).toHaveText('"bar"');
    await expect(outputVariable2Rule).toHaveText('2');
    await expect(outputVariable2Name).toHaveText('output');
    await expect(outputVariable2Value).toHaveText('"baz"');

    /**
     * result assertion (Decision 1)
     */
    await decisionInstancePage.resultTab.click();
    await expect(decisionInstancePage.result).toHaveText(/\["bar","baz"\]/);

    await drdPanel.getByText('Decision 2').click();
    await decisionInstancePage.inputsOutputsTab.click();
    await expect(decisionPanel.getByText('Decision 1')).not.toBeVisible();
    await expect(decisionPanel.getByText('Decision 2')).toBeVisible();

    /**
     * output variables assertions (Decision 2)
     */
    await expect(outputVariables.getByRole('row')).toHaveCount(3);
    await expect(outputVariable1Rule).toHaveText('2');
    await expect(outputVariable1Name).toHaveText('output');
    await expect(outputVariable1Value).toHaveText('"bar"');
    await expect(outputVariable2Rule).toHaveText('2');
    await expect(outputVariable2Name).toHaveText('output2');
    await expect(outputVariable2Value).toHaveText('"baz"');

    /**
     * result assertion (Decision 2)
     */
    await decisionInstancePage.resultTab.click();
    await expect(decisionInstancePage.result).toHaveText(
      /\[{"output":"bar","output2":"baz"}\]/,
    );

    await drdPanel.getByText('Decision 1').click();
    await decisionInstancePage.inputsOutputsTab.click();

    /**
     * output variables assertions (Decision 2)
     */
    await expect(outputVariables.getByRole('row')).toHaveCount(3);
    await expect(outputVariable1Rule).toHaveText('1');
    await expect(outputVariable1Name).toHaveText('output');
    await expect(outputVariable1Value).toHaveText('"bar"');
    await expect(outputVariable2Rule).toHaveText('2');
    await expect(outputVariable2Name).toHaveText('output');
    await expect(outputVariable2Value).toHaveText('"baz"');
  });
});
