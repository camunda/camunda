/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {config} from '../config';
import {setup} from './Variables.setup';
import {screen, within} from '@testing-library/testcafe';
import {demoUser} from './utils/Roles';
import {ClientFunction} from 'testcafe';
import {wait} from './utils/common';

const getURL = ClientFunction(() => window.location.href);

fixture('Variables')
  .page(config.endpoint)
  .before(async () => {
    await setup();
    await wait();
  })

  .beforeEach(async (t) => {
    await t.useRole(demoUser);
  });

test('display info message when task has no variables', async (t) => {
  await t.click(
    within(screen.getByTestId('expanded-panel'))
      .getAllByText('usertask_without_variables')
      .nth(0),
  );
  await t.expect(screen.queryByText('Task has no Variables').exists).ok();
});

test('display variables when task has variables', async (t) => {
  await t.click(
    within(screen.getByTestId('expanded-panel'))
      .getAllByText('usertask_with_variables')
      .nth(0),
  );
  await t.expect(screen.queryByText('Task has no Variables').exists).notOk();

  const withinVariablesTable = within(screen.getByTestId('variables-table'));
  await t
    .expect(
      withinVariablesTable.getByRole('columnheader', {name: 'Name'}).exists,
    )
    .ok();
  await t
    .expect(
      withinVariablesTable.getByRole('columnheader', {name: 'Value'}).exists,
    )
    .ok();
  await t
    .expect(withinVariablesTable.getByRole('cell', {name: 'testData'}).exists)
    .ok();
  await t
    .expect(
      withinVariablesTable.getByRole('cell', {name: '"something"'}).exists,
    )
    .ok();
});

test.after(async (t) => {
  await t
    .click(screen.getByRole('button', {name: 'Unclaim'}))
    .expect(screen.queryByRole('button', {name: 'Claim'}).exists)
    .ok();
})('new variable disappears after refresh', async (t) => {
  await t
    .click(
      within(screen.getByTestId('expanded-panel'))
        .getAllByText('usertask_with_variables')
        .nth(0),
    )
    .expect(screen.queryByRole('button', {name: 'Add Variable'}).exists)
    .notOk()
    .click(screen.getByRole('button', {name: 'Claim'}))
    .click(screen.getByRole('button', {name: 'Add Variable'}));

  await t.typeText(
    screen.queryByLabelText(/1st variable name/i),
    'newVariableName',
    {
      paste: true,
    },
  );

  await t.typeText(
    screen.queryByLabelText(/1st variable value/i),
    '"newVariableValue"',
    {
      paste: true,
    },
  );

  await t.navigateTo(await getURL());

  await t
    .expect(screen.queryByDisplayValue('newVariableName').exists)
    .notOk()
    .expect(screen.queryByDisplayValue('"newVariableValue"').exists)
    .notOk();
});

test.skip('new variable still exists after refresh if task is completed', async (t) => {
  await t
    .click(
      within(screen.getByTestId('expanded-panel'))
        .getAllByText('usertask_with_variables')
        .nth(0),
    )
    .expect(screen.queryByRole('button', {name: 'Add Variable'}).exists)
    .notOk()
    .click(screen.getByRole('button', {name: 'Claim'}))
    .click(screen.getByRole('button', {name: 'Add Variable'}));

  await t.typeText(
    screen.queryByLabelText(/1st variable name/i),
    'newVariableName',
    {
      paste: true,
    },
  );

  await t.typeText(screen.queryByLabelText(/1st variable value/i), '"newVal"', {
    paste: true,
  });

  const currentUrl = await getURL();

  await t
    .expect(
      screen
        .getByRole('button', {name: 'Complete Task'})
        .hasAttribute('disabled'),
    )
    .notOk()
    .click(screen.getByRole('button', {name: 'Complete Task'}))
    .expect(screen.queryByText('Pick a task to work on').exists)
    .ok();

  await t.navigateTo(currentUrl);

  await t
    .expect(screen.queryByText('newVariableName').exists)
    .ok()
    .expect(screen.getByText('"newVal"').exists)
    .ok();
});

test.after(async (t) => {
  await t
    .click(screen.getByRole('button', {name: 'Unclaim'}))
    .expect(screen.queryByRole('button', {name: 'Claim'}).exists)
    .ok();
})('edited variable is not saved after refresh', async (t) => {
  await t
    .click(
      within(screen.getByTestId('expanded-panel'))
        .getAllByText('usertask_with_variables')
        .nth(0),
    )
    .click(screen.getByRole('button', {name: 'Claim'}))
    .expect(screen.queryByDisplayValue('"something"').exists)
    .ok()
    .selectText(screen.queryByLabelText(/testdata value/i))
    .pressKey('delete')
    .typeText(screen.queryByLabelText(/testdata value/i), '"updatedValue"', {
      paste: true,
    });

  await t.navigateTo(await getURL());

  await t.expect(screen.queryByDisplayValue('"something"').exists).ok();
});

test('edited variable is saved after refresh if task is completed', async (t) => {
  await t
    .click(
      within(screen.getByTestId('expanded-panel'))
        .getAllByText('usertask_with_variables')
        .nth(0),
    )
    .click(screen.getByRole('button', {name: 'Claim'}))
    .expect(screen.queryByDisplayValue('"something"').exists)
    .ok()
    .selectText(screen.queryByLabelText(/testdata value/i))
    .pressKey('delete')
    .typeText(screen.queryByLabelText(/testdata value/i), '"updatedValue"', {
      paste: true,
    });

  const currentUrl = await getURL();
  await t
    .expect(
      screen
        .getByRole('button', {name: 'Complete Task'})
        .hasAttribute('disabled'),
    )
    .notOk()
    .click(screen.getByRole('button', {name: 'Complete Task'}))
    .expect(screen.queryByText('Pick a task to work on').exists)
    .ok();

  await t.navigateTo(currentUrl);
  await t.expect(screen.queryByText('"updatedValue"').exists).ok();
});
