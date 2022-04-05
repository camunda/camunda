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
  await t.expect(screen.getByText('Task has no Variables').exists).ok();
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
    .expect(
      withinVariablesTable.getByRole('columnheader', {name: 'testData'}).exists,
    )
    .ok();
  await t
    .expect(
      withinVariablesTable.getByRole('cell', {name: '"something"'}).exists,
    )
    .ok();
});

test.after(async (t) => {
  await t.click(screen.getByRole('button', {name: 'Unclaim'}));
  await t.expect(screen.getByRole('button', {name: 'Claim'}).exists).ok();
})('new variable disappears after refresh', async (t) => {
  await t.click(
    within(screen.getByTestId('expanded-panel'))
      .getAllByText('usertask_with_variables')
      .nth(0),
  );

  await t
    .expect(screen.queryByRole('button', {name: 'Add Variable'}).exists)
    .notOk();

  await t
    .click(screen.getByRole('button', {name: 'Claim'}))
    .click(screen.getByRole('button', {name: 'Add Variable'}));

  await t.typeText(
    within(screen.getByTestId('newVariables[0].name').shadowRoot()).getByRole(
      'textbox',
    ),
    'newVariableName',
  );
  await t.typeText(
    within(screen.getByTestId('newVariables[0].value').shadowRoot()).getByRole(
      'textbox',
    ),
    '"newVariableValue"',
  );

  await t.navigateTo(await getURL());

  await t
    .expect(screen.queryByDisplayValue('newVariableName').exists)
    .notOk()
    .expect(screen.queryByDisplayValue('"newVariableValue"').exists)
    .notOk();
});

test('new variable still exists after refresh if task is completed', async (t) => {
  await t.click(
    within(screen.getByTestId('expanded-panel'))
      .getAllByText('usertask_with_variables')
      .nth(0),
  );

  await t
    .expect(screen.queryByRole('button', {name: 'Add Variable'}).exists)
    .notOk();

  await t
    .click(screen.getByRole('button', {name: 'Claim'}))
    .click(screen.getByRole('button', {name: 'Add Variable'}));

  await t.typeText(
    within(screen.getByTestId('newVariables[0].name').shadowRoot()).getByRole(
      'textbox',
    ),
    'newVariableName',
  );

  await t.typeText(
    within(screen.getByTestId('newVariables[0].value').shadowRoot()).getByRole(
      'textbox',
    ),
    '"newVariableValue"',
  );

  const currentUrl = await getURL();
  await t
    .expect(
      screen
        .getByRole('button', {name: 'Complete Task'})
        .hasAttribute('disabled'),
    )
    .notOk();
  await t.click(screen.getByRole('button', {name: 'Complete Task'}));

  await t
    .expect(screen.getByText('Select a Task to view the details').exists)
    .ok();

  await t.navigateTo(currentUrl);

  await t
    .expect(screen.getByText('newVariableName').exists)
    .ok()
    .expect(screen.getByText('"newVariableValue"').exists)
    .ok();
});

test.after(async (t) => {
  await t.click(screen.getByRole('button', {name: 'Unclaim'}));
  await t.expect(screen.getByRole('button', {name: 'Claim'}).exists).ok();
})('edited variable is not saved after refresh', async (t) => {
  const variableValueField = within(
    screen.getByTestId('variable-value-#testData').shadowRoot(),
  ).getByRole('textbox');

  await t.click(
    within(screen.getByTestId('expanded-panel'))
      .getAllByText('usertask_with_variables')
      .nth(0),
  );

  await t.click(screen.getByRole('button', {name: 'Claim'}));
  await t.expect(variableValueField.value).eql('"something"');

  await t
    .selectText(variableValueField)
    .pressKey('delete')
    .typeText(variableValueField, '"updatedValue"');

  await t
    .click(screen.queryByTestId('logo'))
    .click(
      within(screen.getByTestId('expanded-panel'))
        .getAllByText('usertask_with_variables')
        .nth(0),
    );

  await t.expect(variableValueField.value).eql('"something"');
});

test('edited variable is saved after refresh if task is completed', async (t) => {
  await t.click(
    within(screen.getByTestId('expanded-panel'))
      .getAllByText('usertask_with_variables')
      .nth(0),
  );

  const variableValueField = within(
    screen.getByTestId('variable-value-#testData').shadowRoot(),
  ).getByRole('textbox');

  await t.click(screen.getByRole('button', {name: 'Claim'}));
  await t.expect(variableValueField.value).eql('"something"');

  await t
    .selectText(variableValueField)
    .pressKey('delete')
    .typeText(variableValueField, '"updatedValue"');

  const currentUrl = await getURL();
  await t
    .expect(
      screen
        .getByRole('button', {name: 'Complete Task'})
        .hasAttribute('disabled'),
    )
    .notOk();
  await t.click(screen.getByRole('button', {name: 'Complete Task'}));

  await t
    .expect(screen.getByText('Select a Task to view the details').exists)
    .ok();
  await t.navigateTo(currentUrl);
  await t.expect(screen.getByText('"updatedValue"').exists).ok();
});
