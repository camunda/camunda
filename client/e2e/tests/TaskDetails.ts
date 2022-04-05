/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {config} from '../config';
import {setup} from './TaskDetails.setup';
import {screen, within} from '@testing-library/testcafe';
import {demoUser} from './utils/Roles';
import {wait} from './utils/common';
import {ClientFunction} from 'testcafe';

fixture('Task Details')
  .page(config.endpoint)
  .before(async () => {
    await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
  });

const getURL = ClientFunction(() => window.location.href);

test('load task details when a task is selected', async (t) => {
  const withinExpandedPanel = within(screen.getByTestId('expanded-panel'));

  await t.click(
    withinExpandedPanel.getAllByText('usertask_to_be_completed').nth(0),
  );

  await t.expect(screen.queryByTestId('details-table').exists).ok();

  const withinDetailsTable = within(screen.getByTestId('details-table'));

  await t
    .expect(withinDetailsTable.getByRole('columnheader', {name: 'Name'}).exists)
    .ok()
    .expect(
      withinDetailsTable.getByRole('cell', {name: 'Some user activity'}).exists,
    )
    .ok();

  await t
    .expect(
      withinDetailsTable.getByRole('columnheader', {name: 'Process'}).exists,
    )
    .ok()
    .expect(
      withinDetailsTable.getByRole('cell', {name: 'usertask_to_be_completed'})
        .exists,
    )
    .ok();

  await t
    .expect(
      withinDetailsTable.getByRole('columnheader', {name: 'Creation Time'})
        .exists,
    )
    .ok();

  await t
    .expect(
      withinDetailsTable.getByRole('columnheader', {name: 'Assignee'}).exists,
    )
    .ok()
    .expect(withinDetailsTable.getByRole('button', {name: 'Claim'}).exists)
    .ok()
    .expect(
      within(screen.getByTestId('assignee-task-details')).getByText('--')
        .exists,
    )
    .ok();
});

test('claim and unclaim task', async (t) => {
  const withinExpandedPanel = within(screen.getByTestId('expanded-panel'));

  await t.click(
    withinExpandedPanel.getAllByText('usertask_to_be_completed').nth(0),
  );

  await t.expect(screen.queryByTestId('details-table').exists).ok();

  await t
    .expect(screen.getByRole('button', {name: 'Claim'}).exists)
    .ok()
    .expect(screen.queryByRole('button', {name: 'Complete Task'}).exists)
    .notOk();

  await t.click(screen.getByRole('button', {name: 'Claim'}));

  await t
    .expect(await screen.findByRole('button', {name: 'Unclaim'}).exists)
    .ok()
    .expect(
      within(screen.getByTestId('assignee-task-details')).getByText('demo')
        .exists,
    )
    .ok()
    .expect(screen.getByRole('button', {name: 'Complete Task'}).exists)
    .ok();

  await t.click(screen.getByRole('button', {name: 'Unclaim'}));

  await t
    .expect(await screen.findByRole('button', {name: 'Claim'}).exists)
    .ok()
    .expect(
      within(screen.getByTestId('assignee-task-details')).getByText('--')
        .exists,
    )
    .ok()
    .expect(screen.queryByRole('button', {name: 'Complete Task'}).exists)
    .notOk();
});

test('complete task', async (t) => {
  const withinExpandedPanel = within(screen.getByTestId('expanded-panel'));

  await t.click(withinExpandedPanel.getByText('usertask_to_be_completed'));

  await t.expect(screen.queryByTestId('details-table').exists).ok();

  const withinDetailsTable = within(screen.getByTestId('details-table'));

  await t
    .expect(
      withinDetailsTable.queryByRole('columnheader', {name: 'Completion Time'})
        .exists,
    )
    .notOk();

  const currentUrl = await getURL();

  await t
    .click(withinDetailsTable.getByRole('button', {name: 'Claim'}))
    .click(screen.getByRole('button', {name: 'Complete Task'}));

  await t
    .expect(screen.getByText('Select a Task to view the details').exists)
    .ok();

  await t.navigateTo(currentUrl);

  await t.expect(screen.queryByTestId('details-table').exists).ok();

  await t
    .expect(
      withinDetailsTable.getByRole('columnheader', {name: 'Completion Time'})
        .exists,
    )
    .ok();
});

test('task completion with form', async (t) => {
  await t
    .click(screen.findAllByText(/^user registration$/i).nth(0))
    .click(screen.findByRole('button', {name: /claim/i}))
    .typeText(screen.findByLabelText(/name/i), 'Jon')
    .typeText(screen.findByLabelText(/address/i), 'Earth')
    .typeText(screen.findByLabelText(/age/i), '21')
    .click(screen.getByRole('button', {name: /complete task/i}))
    .click(screen.getByRole('combobox', {name: /filter/i}))
    .click(screen.findByRole('option', {name: /completed/i}))
    .click(screen.findByText(/^user registration$/i))
    .expect(screen.findByLabelText(/name/i).value)
    .eql('Jon')
    .expect(screen.findByLabelText(/address/i).value)
    .eql('Earth')
    .expect(screen.findByLabelText(/age/i).value)
    .eql('21');
});

test('task completion with form on Claimed by Me filter', async (t) => {
  await t
    .click(screen.findByText(/^user registration$/i))
    .click(screen.findByRole('button', {name: /claim/i}))
    .expect(
      screen
        .getByRole('button', {name: /complete task/i})
        .hasAttribute('disabled'),
    )
    .notOk()
    .click(screen.getByRole('combobox', {name: /filter/i}))
    .click(screen.findByRole('option', {name: /claimed by me/i}))
    .click(screen.findByText(/^user registration$/i))
    .typeText(screen.findByLabelText(/name/i), 'Gaius Julius Caesar')
    .typeText(screen.findByLabelText(/address/i), 'Rome')
    .typeText(screen.findByLabelText(/age/i), '55')
    .click(screen.getByRole('button', {name: /complete task/i}))
    .expect(screen.findByText(/^user registration$/i).exists)
    .notOk();
});

test('task completion with prefilled form', async (t) => {
  await t
    .click(screen.findByText(/user registration with vars/i))
    .click(screen.findByRole('button', {name: /claim/i}))
    .typeText(screen.findByDisplayValue(/jane/i), 'Jon', {replace: true})
    .typeText(screen.findByLabelText(/address/i), 'Earth')
    .typeText(screen.findByDisplayValue(/50/i), '21', {replace: true})
    .click(screen.getByRole('button', {name: /complete task/i}))
    .click(screen.getByRole('combobox', {name: /filter/i}))
    .click(screen.findByRole('option', {name: /completed/i}))
    .click(screen.findByText(/user registration with vars/i))
    .expect(screen.findByLabelText(/name/i).value)
    .eql('Jon')
    .expect(screen.findByLabelText(/address/i).value)
    .eql('Earth')
    .expect(screen.findByLabelText(/age/i).value)
    .eql('21');
});

test('should rerender forms properly', async (t) => {
  await t
    .click(screen.findByText(/user task with form rerender 1/i))
    .expect(screen.findByDisplayValue(/mary/i).exists)
    .ok()
    .click(screen.findByText(/user task with form rerender 2/i))
    .expect(screen.findByDisplayValue(/stuart/i).exists)
    .ok();
});
