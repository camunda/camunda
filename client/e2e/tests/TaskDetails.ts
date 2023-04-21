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

fixture('Task details')
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
  await t.click(
    within(screen.getByLabelText('Left panel'))
      .getAllByText('usertask_to_be_completed')
      .nth(0),
  );

  const taskDetailsHeader = within(screen.getByTitle('Task details header'));

  await t
    .expect(taskDetailsHeader.queryByText('Some user activity').exists)
    .ok()
    .expect(taskDetailsHeader.getByText('usertask_to_be_completed').exists)
    .ok()
    .expect(taskDetailsHeader.getByText('Unassigned').exists)
    .ok()
    .expect(
      taskDetailsHeader.getByRole('button', {name: 'Assign to me'}).exists,
    )
    .ok();

  const withinDetailsPanel = within(screen.getByLabelText('Details'));

  await t
    .expect(withinDetailsPanel.getByText('Creation date').exists)
    .ok()
    .expect(withinDetailsPanel.getByText('Completion date').exists)
    .ok()
    .expect(withinDetailsPanel.getByText('Due date').exists)
    .ok()
    .expect(withinDetailsPanel.getByText('Follow up date').exists)
    .ok();
});

test('assign and unassign task', async (t) => {
  await t.click(
    within(screen.getByLabelText('Left panel'))
      .getAllByText('usertask_to_be_completed')
      .nth(0),
  );

  const taskDetailsHeader = within(screen.getByTitle('Task details header'));

  await t
    .expect(screen.getByRole('button', {name: 'Assign to me'}).exists)
    .ok()
    .expect(
      screen
        .queryByRole('button', {name: 'Complete Task'})
        .hasAttribute('disabled'),
    )
    .ok()
    .click(screen.getByRole('button', {name: 'Assign to me'}))
    .expect(screen.queryByRole('button', {name: 'Unassign'}).exists)
    .ok()
    .expect(taskDetailsHeader.getByText('Assigned').exists)
    .ok()
    .expect(
      screen
        .getByRole('button', {name: 'Complete Task'})
        .hasAttribute('disabled'),
    )
    .notOk()
    .click(screen.getByRole('button', {name: 'Unassign'}))
    .expect(screen.queryByRole('button', {name: 'Assign to me'}).exists)
    .ok()
    .expect(taskDetailsHeader.getByText('Unassigned').exists)
    .ok()
    .expect(
      screen
        .queryByRole('button', {name: 'Complete Task'})
        .hasAttribute('disabled'),
    )
    .ok();
});

test('complete task', async (t) => {
  await t.click(
    within(screen.getByLabelText('Left panel')).getByText(
      'usertask_to_be_completed',
    ),
  );

  await t
    .expect(
      within(screen.queryByLabelText('Details')).queryByText('Pending task')
        .exists,
    )
    .ok();

  const currentUrl = await getURL();

  await t
    .click(screen.getByRole('button', {name: 'Assign to me'}))
    .click(screen.getByRole('button', {name: 'Complete Task'}));

  await t
    .expect(
      screen.queryByRole('heading', {name: 'Pick a task to work on'}).exists,
    )
    .ok();

  await t.navigateTo(currentUrl);

  await t
    .expect(screen.queryByLabelText('Details').exists)
    .ok()
    .expect(
      within(screen.queryByLabelText('Details')).queryByText('Pending task')
        .exists,
    )
    .notOk();
});

test('task completion with form', async (t) => {
  await t
    .click(screen.queryAllByText(/^user registration$/i).nth(0))
    .click(screen.queryByRole('button', {name: /assign/i}))
    .typeText(screen.queryByLabelText(/name/i), 'Jon')
    .typeText(screen.queryByLabelText(/address/i), 'Earth')
    .typeText(screen.queryByLabelText(/age/i), '21')
    .click(screen.getByRole('button', {name: /complete task/i}))
    .click(screen.queryByText('All open'))
    .click(screen.queryByText('Completed'))
    .click(screen.queryByText(/^user registration$/i))
    .expect(screen.queryByLabelText(/name/i).value)
    .eql('Jon')
    .expect(screen.queryByLabelText(/address/i).value)
    .eql('Earth')
    .expect(screen.queryByLabelText(/age/i).value)
    .eql('21');
});

test('task completion with form on Assigned to Me filter', async (t) => {
  await t
    .click(screen.queryByText(/^user registration$/i))
    .click(screen.queryByRole('button', {name: /assign/i}))
    .expect(
      screen
        .getByRole('button', {name: /complete task/i})
        .hasAttribute('disabled'),
    )
    .notOk()
    .click(screen.queryByText('All open'))
    .click(screen.queryByText('Assigned to me'))
    .click(
      within(screen.getByLabelText('Left panel')).queryByText(
        /^user registration$/i,
      ),
    )
    .typeText(screen.queryByLabelText(/name/i), 'Gaius Julius Caesar')
    .typeText(screen.queryByLabelText(/address/i), 'Rome')
    .typeText(screen.queryByLabelText(/age/i), '55')
    .click(screen.getByRole('button', {name: /complete task/i}))
    .expect(screen.queryByText(/^user registration$/i).exists)
    .notOk();
});

test('task completion with prefilled form', async (t) => {
  await t
    .click(screen.queryByText(/user registration with vars/i))
    .click(screen.queryByRole('button', {name: /assign/i}))
    .typeText(screen.queryByDisplayValue(/jane/i), 'Jon', {replace: true})
    .typeText(screen.queryByLabelText(/address/i), 'Earth')
    .typeText(screen.queryByDisplayValue(/50/i), '21', {replace: true})
    .click(screen.getByRole('button', {name: /complete task/i}))
    .click(screen.queryByText('All open'))
    .click(screen.queryByText('Completed'))
    .click(screen.queryByText(/user registration with vars/i))
    .expect(screen.queryByLabelText(/name/i).value)
    .eql('Jon')
    .expect(screen.queryByLabelText(/address/i).value)
    .eql('Earth')
    .expect(screen.queryByLabelText(/age/i).value)
    .eql('21');
});

test('should rerender forms properly', async (t) => {
  await t
    .click(screen.queryByText(/user task with form rerender 1/i))
    .expect(screen.queryByDisplayValue(/mary/i).exists)
    .ok()
    .click(screen.queryByText(/user task with form rerender 2/i))
    .expect(screen.queryByDisplayValue(/stuart/i).exists)
    .ok();
});
