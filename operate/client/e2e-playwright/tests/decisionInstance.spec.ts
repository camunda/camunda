/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {setup} from './decisionInstance.mocks';
import {test} from '../test-fixtures';
import {SETUP_WAITING_TIME} from './constants';
import {expect} from '@playwright/test';
import {config} from '../config';
import {evaluateDecision} from '../setup-utils';

test.describe('Decision Instance', () => {
  let initialData: Awaited<ReturnType<typeof setup>>;

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

    await evaluateDecision({
      decisionKey: initialData.decision1Key!.toString(),
      variables: {input: 'foo'},
    });

    // Wait for decision instances to be available in Operate
    await expect
      .poll(
        async () => {
          const response = await request.post(
            `${config.endpoint}/api/decision-instances`,
            {
              data: {
                query: {evaluated: true, failed: true},
              },
            },
          );
          const responseData = await response.json();
          return responseData.totalCount;
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBe(2);
  });

  test('Switching between decisions', async ({
    decisionInstancePage,
    request,
  }) => {
    /**
     * get decision instance key
     */
    const response = await request.post(
      `${config.endpoint}/api/decision-instances`,
      {
        data: {
          query: {evaluated: true, failed: true},
          sorting: {sortBy: 'evaluationDate', sortOrder: 'desc'},
        },
      },
    );
    const responseData = await response.json();
    const decisionInstanceKey = responseData.decisionInstances[0].id;

    await decisionInstancePage.navigateToDecisionInstance({
      decisionInstanceKey,
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
