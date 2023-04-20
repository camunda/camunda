/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {config} from '../config';
import {demoUser} from './utils/Roles';
import {setup} from './DecisionInstances.setup';
import {wait} from './utils/wait';
import {screen, within} from '@testing-library/testcafe';
import {decisionsPage} from './PageModels/Decisions';
import {setDecisionsFlyoutTestAttribute} from './utils/setFlyoutTestAttribute';
import {IS_COMBOBOX_ENABLED} from '../../src/modules/feature-flags';
import {clearComboBox} from './utils/clearComboBox';

fixture('Decision Instances')
  .page(config.endpoint)
  .before(async (ctx) => {
    await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser).maximizeWindow();
  });

test('Switch between Decision versions', async (t) => {
  await t.click(
    screen.queryByRole('link', {
      name: /decisions/i,
    })
  );

  if (!IS_COMBOBOX_ENABLED) {
    await setDecisionsFlyoutTestAttribute('decisionName');
    await setDecisionsFlyoutTestAttribute('decisionVersion');
  }

  const withinDecisionViewer = within(screen.getByTestId('decision-viewer'));

  await decisionsPage.selectDecision('Decision 1');
  await decisionsPage.selectVersion('1');
  await t
    .expect(withinDecisionViewer.queryByText('Decision 1').exists)
    .ok()
    .expect(withinDecisionViewer.queryByText('Version 1').exists)
    .ok();

  await decisionsPage.selectVersion('2');
  await t
    .expect(withinDecisionViewer.queryByText('Decision 1').exists)
    .ok()
    .expect(withinDecisionViewer.queryByText('Version 2').exists)
    .ok();

  await clearComboBox({fieldName: 'Name'});
  await decisionsPage.selectDecision('Decision 2');
  await t.expect(withinDecisionViewer.queryByText('Decision 2').exists).ok();

  await decisionsPage.selectVersion('1');
  await t.expect(withinDecisionViewer.queryByText('Decision 2').exists).ok();
  await t.expect(withinDecisionViewer.queryByText('Version 1').exists).ok();

  await decisionsPage.selectVersion('2');
  await t.expect(withinDecisionViewer.queryByText('Decision 2').exists).ok();
  await t.expect(withinDecisionViewer.queryByText('Version 2').exists).ok();
});
