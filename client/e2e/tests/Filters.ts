/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {config} from '../config';
import {setup} from './Filters.setup';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {screen, within} from '@testing-library/testcafe';
import {ClientFunction} from 'testcafe';

fixture('Filters')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
    await t.maximizeWindow();
    await t.navigateTo(`${config.endpoint}/#/instances`);
  });

const getPathname = ClientFunction(() => window.location.hash);

test('Initial url and filters should be correct', async (t) => {
  await t
    .expect(await getPathname())
    .eql(`#/instances?filter={${encodeURI(`"active":true,"incidents":true`)}}`);

  await t
    .expect(screen.getByRole('checkbox', {name: 'Running Instances'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Finished Instances'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Completed'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Canceled'}).checked)
    .notOk();
});

test('Navigating in header should affect filters and url correctly', async (t) => {
  await t.click(screen.getByRole('listitem', {name: 'Incidents'}));
  await t
    .expect(await getPathname())
    .eql(`#/instances?filter={${encodeURI(`"incidents":true`)}}`);

  await t
    .expect(screen.getByRole('checkbox', {name: 'Running Instances'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Finished Instances'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Completed'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Canceled'}).checked)
    .notOk();

  await t.click(screen.getByRole('listitem', {name: 'Running Instances'}));
  await t
    .expect(await getPathname())
    .eql(`#/instances?filter={${encodeURI(`"active":true,"incidents":true`)}}`);

  await t
    .expect(screen.getByRole('checkbox', {name: 'Running Instances'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Finished Instances'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Completed'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Canceled'}).checked)
    .notOk();
});

test('Instance IDs filter', async (t) => {
  const instanceId = await within(screen.getByTestId('instances-list'))
    .getAllByRole('link', {name: /View instance/i})
    .nth(0).innerText;

  await t.typeText(
    screen.getByRole('textbox', {
      name: 'Instance Id(s) separated by space or comma',
    }),
    instanceId.toString(),
    {
      paste: true,
    }
  );

  // wait for filter to be applied, see there is only 1 result
  await t
    .expect(
      within(screen.getByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(1);

  // changes reflected in the url
  await t
    .expect(await getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"ids":"${instanceId}"`
      )}}`
    );

  // result is the one we filtered
  await t
    .expect(
      await within(screen.getByTestId('instances-list'))
        .getAllByRole('link', {name: /View instance/i})
        .nth(0).innerText
    )
    .eql(instanceId);

  await t.click(screen.getByRole('button', {name: /reset filters/i}));

  // wait for reset filter to be applied, see there is more than one result again
  await t
    .expect(
      within(screen.getByTestId('instances-list')).getAllByRole('row').count
    )
    .gt(1);

  // filter has been reset
  await t
    .expect(
      await screen.getByRole('textbox', {
        name: 'Instance Id(s) separated by space or comma',
      }).value
    )
    .eql('');

  // changes reflected in the url
  await t
    .expect(await getPathname())
    .eql(`#/instances?filter={${encodeURI(`"active":true,"incidents":true`)}}`);
});

test('Error Message filter', async (t) => {
  const instanceCount = await within(
    screen.getByTestId('instances-list')
  ).getAllByRole('row').count;

  const errorMessage =
    "failed to evaluate expression 'nonExistingClientId': no variable found for name 'nonExistingClientId'";
  await t.typeText(
    screen.getByRole('textbox', {name: /error message/i}),
    errorMessage,
    {
      paste: true,
    }
  );

  // wait for filter to be applied, see results are narrowed down.
  await t
    .expect(
      within(screen.getByTestId('instances-list')).getAllByRole('row').count
    )
    .lt(instanceCount);

  // changes reflected in the url
  await t
    .expect(await getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"errorMessage":"`
      )}${encodeURIComponent(errorMessage)}${encodeURI('"')}}`
    );

  await t.click(screen.getByRole('button', {name: /reset filters/i}));

  // wait for reset filter to be applied, see there is more than one result again.
  await t
    .expect(
      within(screen.getByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(instanceCount);

  // filter has been reset
  await t
    .expect(screen.getByRole('textbox', {name: /error message/i}).value)
    .eql('');

  // changes reflected in the url
  await t
    .expect(await getPathname())
    .eql(`#/instances?filter={${encodeURI(`"active":true,"incidents":true`)}}`);
});

//https://jira.camunda.com/browse/OPE-1098
test.skip('End Date filter', async (t) => {
  const {
    initialData: {instanceToCancel},
  } = t.fixtureCtx;

  await t.typeText(
    screen.getByRole('textbox', {
      name: 'Instance Id(s) separated by space or comma',
    }),
    instanceToCancel.workflowInstanceKey,
    {
      paste: true,
    }
  );

  // wait for filter to be applied
  await t
    .expect(
      within(screen.getByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(1);
  await t.click(screen.getByRole('button', {name: /cancel instance/i}));
  // wait for operation to be completed
  await t
    .expect(
      screen.queryByText('There are no Instances matching this filter set')
        .exists
    )
    .ok();

  await t.click(screen.getByRole('checkbox', {name: 'Finished Instances'}));

  // wait for filter to be applied
  await t
    .expect(
      within(screen.getByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(1);

  // get end date from recently canceled instance
  const endDate = await within(
    screen.getByTestId('instances-list')
  ).getByTestId('end-time').innerText;

  // reset the filters to start over
  await t.click(screen.getByRole('button', {name: /reset filters/i}));

  const instanceCount = await within(
    screen.getByTestId('instances-list')
  ).getAllByRole('row').count;

  await t
    .click(screen.getByRole('checkbox', {name: 'Finished Instances'}))
    .typeText(screen.getByRole('textbox', {name: /end date/i}), endDate, {
      paste: true,
    });

  // wait for filter to be applied, see results are narrowed down.
  await t
    .expect(
      within(screen.getByTestId('instances-list')).getAllByRole('row').count
    )
    .lt(instanceCount);

  await t
    .expect(await getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"completed":true,"canceled":true,"endDate":"${endDate}"`
      )}}`
    );

  await t.click(screen.getByRole('button', {name: /reset filters/i}));

  // wait for filter to be applied, see there are more results again.
  await t
    .expect(
      within(screen.getByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(instanceCount);

  // filter has been reset
  await t
    .expect(screen.getByRole('textbox', {name: /end date/i}).value)
    .eql('');

  // changes reflected in the url
  await t
    .expect(await getPathname())
    .eql(`#/instances?filter={${encodeURI(`"active":true,"incidents":true`)}}`);
});

test('Variable filter', async (t) => {
  const instanceCount = await within(
    screen.getByTestId('instances-list')
  ).getAllByRole('row').count;

  await t.typeText(
    screen.getByRole('textbox', {name: /variable/i}),
    'filtersTest',
    {
      paste: true,
    }
  );

  await t.typeText(screen.getByRole('textbox', {name: /value/i}), '123', {
    paste: true,
  });

  // wait for filter to be applied, see results are narrowed down.
  await t
    .expect(
      within(screen.getByTestId('instances-list')).getAllByRole('row').count
    )
    .lt(instanceCount);

  await t
    .expect(await getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"variable":`
      )}{${encodeURI(`"name":"filtersTest","value":"123"`)}}}`
    );

  await t.click(screen.getByRole('button', {name: /reset filters/i}));

  // wait for filter to be applied, see there is more than one result again.
  await t
    .expect(
      within(screen.getByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(instanceCount);

  // filter has been reset
  await t
    .expect(screen.getByRole('textbox', {name: /variable/i}).value)
    .eql('');

  await t.expect(screen.getByRole('textbox', {name: /value/i}).value).eql('');

  await t
    .expect(await getPathname())
    .eql(`#/instances?filter={${encodeURI(`"active":true,"incidents":true`)}}`);
});

//https://jira.camunda.com/browse/OPE-1098
test.skip('Operation ID filter', async (t) => {
  const {
    initialData: {instanceToCancelForOperations},
  } = t.fixtureCtx;

  await t.typeText(
    screen.getByRole('textbox', {
      name: 'Instance Id(s) separated by space or comma',
    }),
    instanceToCancelForOperations.workflowInstanceKey,
    {
      paste: true,
    }
  );

  // wait for filter to be applied
  await t
    .expect(
      within(screen.getByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(1);
  await t.click(screen.getByRole('button', {name: /cancel instance/i}));
  // wait for operation to be completed
  await t
    .expect(
      screen.queryByText('There are no Instances matching this filter set')
        .exists
    )
    .ok();

  await t.click(screen.getByRole('button', {name: 'Expand Operations'}));
  const operationId = await screen.getAllByTestId('operation-id').nth(0)
    .innerText;

  await t.click(screen.getByRole('button', {name: 'Collapse Operations'}));

  // reset the filters to start over
  await t.click(screen.getByRole('button', {name: /reset filters/i}));

  const instanceCount = await within(
    screen.getByTestId('instances-list')
  ).getAllByRole('row').count;

  await t
    .click(screen.getByRole('checkbox', {name: 'Finished Instances'}))
    .typeText(
      screen.getByRole('textbox', {name: /operation id/i}),
      operationId,
      {
        paste: true,
      }
    );

  // wait for filter to be applied
  await t
    .expect(
      within(screen.getByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(1);

  await t
    .expect(await getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"completed":true,"canceled":true,"batchOperationId":"${operationId}"`
      )}}`
    );

  await t.click(screen.getByRole('button', {name: /reset filters/i}));

  // wait for filter to be applied, see there are more results again.
  await t
    .expect(
      within(screen.getByTestId('instances-list')).getAllByRole('row').count
    )
    .eql(instanceCount);

  // filter has been reset
  await t
    .expect(screen.getByRole('textbox', {name: /operation id/i}).value)
    .eql('');

  await t
    .expect(await getPathname())
    .eql(`#/instances?filter={${encodeURI(`"active":true,"incidents":true`)}}`);
});

test('Checkboxes', async (t) => {
  await t
    .click(screen.getByRole('checkbox', {name: 'Running Instances'}))
    .expect(screen.getByRole('checkbox', {name: 'Running Instances'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Finished Instances'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Completed'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Canceled'}).checked)
    .notOk();

  await t.expect(await getPathname()).eql('#/instances?filter={}');

  await t
    .click(screen.getByRole('checkbox', {name: 'Active'}))
    .expect(screen.getByRole('checkbox', {name: 'Running Instances'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Finished Instances'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Completed'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Canceled'}).checked)
    .notOk();

  await t
    .expect(await getPathname())
    .eql(`#/instances?filter={${encodeURI(`"active":true`)}}`);

  await t
    .click(screen.getByRole('checkbox', {name: 'Incidents'}))
    .expect(screen.getByRole('checkbox', {name: 'Running Instances'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Finished Instances'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Completed'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Canceled'}).checked)
    .notOk();

  await t
    .expect(await getPathname())
    .eql(`#/instances?filter={${encodeURI(`"active":true,"incidents":true`)}}`);

  await t
    .click(screen.getByRole('checkbox', {name: 'Finished Instances'}))
    .expect(screen.getByRole('checkbox', {name: 'Running Instances'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Finished Instances'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Completed'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Canceled'}).checked)
    .ok();

  await t
    .expect(await getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"completed":true,"canceled":true`
      )}}`
    );
  await t
    .click(screen.getByRole('checkbox', {name: 'Completed'}))
    .expect(screen.getByRole('checkbox', {name: 'Running Instances'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Finished Instances'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Completed'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Canceled'}).checked)
    .ok();

  await t
    .expect(await getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"canceled":true`
      )}}`
    );
  await t
    .click(screen.getByRole('checkbox', {name: 'Canceled'}))
    .expect(screen.getByRole('checkbox', {name: 'Running Instances'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Finished Instances'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Completed'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Canceled'}).checked)
    .notOk();

  await t
    .expect(await getPathname())
    .eql(`#/instances?filter={${encodeURI(`"active":true,"incidents":true`)}}`);

  await t
    .click(screen.getByRole('checkbox', {name: 'Finished Instances'}))
    .expect(screen.getByRole('checkbox', {name: 'Running Instances'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Finished Instances'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Completed'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Canceled'}).checked)
    .ok();

  await t
    .expect(await getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"completed":true,"canceled":true`
      )}}`
    );

  await t
    .click(screen.getByRole('button', {name: /reset filters/i}))
    .expect(screen.getByRole('checkbox', {name: 'Running Instances'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Finished Instances'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Completed'}).checked)
    .notOk()
    .expect(screen.getByRole('checkbox', {name: 'Canceled'}).checked)
    .notOk();

  await t
    .expect(await getPathname())
    .eql(`#/instances?filter={${encodeURI(`"active":true,"incidents":true`)}}`);
});

test('Workflow Filter', async (t) => {
  const workflowCombobox = screen.getByRole('combobox', {
    name: 'Workflow',
  });

  // select a workflow with multiple versions, see that latest version is selected by default, a diagram is displayed and selected instances are removed
  await t
    .click(workflowCombobox)
    .click(
      within(workflowCombobox).getByRole('option', {
        name: 'Process With Multiple Versions',
      })
    )
    .expect(screen.getByRole('combobox', {name: 'Workflow Version'}).value)
    .eql('2');

  await t
    .expect(await getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"version":"2","workflow":"processWithMultipleVersions"`
      )}}&${encodeURI(`name="Process With Multiple Versions"`)}`
    );

  await t.expect(screen.getByTestId('diagram').exists).ok();

  // select all versions, see that diagram disappeared and selected instances are removed
  await t
    .click(
      screen.getByRole('combobox', {
        name: 'Workflow Version',
      })
    )
    .click(screen.getByRole('option', {name: 'All versions'}))
    .expect(screen.getByRole('combobox', {name: 'Workflow Version'}).value)
    .eql('all')
    .expect(screen.queryByTestId('diagram').exists)
    .notOk()
    .expect(
      screen.getByText(
        'There is more than one Version selected for Workflow "Process With Multiple Versions"'
      ).exists
    )
    .ok()
    .expect(
      screen.getByText('To see a Diagram, select a single Version').exists
    )
    .ok();

  await t
    .expect(await getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"version":"all","workflow":"processWithMultipleVersions"`
      )}}&${encodeURI(`name="Process With Multiple Versions"`)}`
    );

  // reset the filters to start over
  await t.click(screen.getByRole('button', {name: /reset filters/i}));

  // select a workflow and a flow node
  await t
    .click(workflowCombobox)
    .click(
      within(workflowCombobox).getByRole('option', {
        name: 'Process With Multiple Versions',
      })
    )
    .click(screen.getByRole('combobox', {name: /flow node/i}))
    .click(
      screen.getByRole('option', {
        name: 'StartEvent_1',
      })
    )
    .expect(screen.getByRole('combobox', {name: /flow node/i}).value)
    .eql('StartEvent_1');

  await t
    .expect(await getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"activityId":"StartEvent_1","version":"2","workflow":"processWithMultipleVersions"`
      )}}&${encodeURI(`name="Process With Multiple Versions"`)}`
    );

  // change workflow and see flow node filter has been reset
  await t
    .click(workflowCombobox)
    .click(
      within(workflowCombobox).getByRole('option', {
        name: 'Order process',
      })
    )
    .expect(screen.getByRole('combobox', {name: /flow node/i}).value)
    .eql('');

  await t
    .expect(await getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"version":"1","workflow":"orderProcess"`
      )}}&${encodeURI(`name="Order process"`)}`
    );
});

test('Workflow Filter - Interaction with diagram', async (t) => {
  const workflowCombobox = screen.getByRole('combobox', {
    name: 'Workflow',
  });

  await t
    .expect(screen.getByText('There is no Workflow selected').exists)
    .ok()
    .expect(
      screen.getByText(
        'To see a Diagram, select a Workflow in the Filters panel'
      ).exists
    )
    .ok()
    .expect(
      screen
        .getByRole('combobox', {name: /flow node/i})
        .hasAttribute('disabled')
    )
    .ok()
    .expect(
      screen
        .getByRole('combobox', {name: 'Workflow Version'})
        .hasAttribute('disabled')
    )
    .ok()
    .expect(screen.getByRole('combobox', {name: 'Workflow Version'}).value)
    .eql('')
    .expect(screen.getByRole('combobox', {name: /flow node/i}).value)
    .eql('')
    .expect(await getPathname())
    .eql(`#/instances?filter={${encodeURI(`"active":true,"incidents":true`)}}`);

  // select a workflow that has only one version
  await t.click(workflowCombobox).click(
    within(workflowCombobox).getByRole('option', {
      name: 'Order process',
    })
  );

  await t
    .expect(screen.getByTestId('diagram').exists)
    .ok()
    .expect(screen.queryByText('There is no Workflow selected').exists)
    .notOk()
    .expect(
      screen.queryByText(
        'To see a Diagram, select a Workflow in the Filters panel'
      ).exists
    )
    .notOk()
    .expect(
      screen
        .getByRole('combobox', {name: /flow node/i})
        .hasAttribute('disabled')
    )
    .notOk()
    .expect(screen.getByRole('combobox', {name: 'Workflow Version'}).value)
    .eql('1')
    .expect(screen.getByRole('combobox', {name: /flow node/i}).value)
    .eql('')
    .expect(await getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"version":"1","workflow":"orderProcess"`
      )}}&${encodeURI(`name="Order process"`)}`
    );

  // select a flow node without an instance from the diagram
  await t
    .click(within(screen.getByTestId('diagram')).getByText(/ship articles/i))
    .expect(
      screen.getByText('There are no Instances matching this filter set').exists
    )
    .ok()
    .expect(screen.getByRole('combobox', {name: /flow node/i}).value)
    .eql('shipArticles');

  await t
    .expect(await getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"version":"1","workflow":"orderProcess","activityId":"shipArticles"`
      )}}&${encodeURI(`name="Order process"`)}`
    );

  // select a flow node with an instance from the diagram
  await t
    .click(within(screen.getByTestId('diagram')).getByText(/check payment/i))
    .expect(
      screen.queryByText('There are no Instances matching this filter set')
        .exists
    )
    .notOk()
    .expect(screen.getByRole('combobox', {name: /flow node/i}).value)
    .eql('checkPayment');

  await t
    .expect(await getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"version":"1","workflow":"orderProcess","activityId":"checkPayment"`
      )}}&${encodeURI(`name="Order process"`)}`
    );

  // select same flow node again and see filter is removed
  await t.click(
    within(screen.getByTestId('diagram')).getByText(/check payment/i)
  );

  await t
    .expect(await getPathname())
    .eql(
      `#/instances?filter={${encodeURI(
        `"active":true,"incidents":true,"version":"1","workflow":"orderProcess"`
      )}}&${encodeURI(`name="Order process"`)}`
    )
    .expect(screen.getByRole('combobox', {name: /flow node/i}).value)
    .eql('');
});

test('Should set filters from url', async (t) => {
  await t.navigateTo(
    `${config.endpoint}/#/instances?filter={"active":true,"incidents":true,"completed":true,"canceled":true,"ids":"2251799813685255","errorMessage":"some%20error%20message","startDate":"2020-09-10%2018:41:44","endDate":"2020-12-12%2012:12:12","version":"2","workflow":"processWithMultipleVersions","variable":{"name":"test","value":"123"},"batchOperationId":"5be8a137-fbb4-4c54-964c-9c7be98b80e6","activityId":"alwaysFails"}&name="Process%20With%20Multiple%20Versions"`
  );

  await t
    .expect(
      screen.getByRole('combobox', {
        name: 'Workflow',
      }).value
    )
    .eql('processWithMultipleVersions')
    .expect(screen.getByRole('combobox', {name: 'Workflow Version'}).value)
    .eql('2')
    .expect(
      screen.getByRole('textbox', {
        name: 'Instance Id(s) separated by space or comma',
      }).value
    )
    .eql('2251799813685255')
    .expect(screen.getByRole('textbox', {name: /error message/i}).value)
    .eql('some error message')
    .expect(screen.getByRole('textbox', {name: /start date/i}).value)
    .eql('2020-09-10 18:41:44')
    .expect(screen.getByRole('textbox', {name: /end date/i}).value)
    .eql('2020-12-12 12:12:12')
    .expect(screen.getByRole('combobox', {name: /flow node/i}).value)
    .eql('alwaysFails')
    .expect(screen.getByRole('textbox', {name: /variable/i}).value)
    .eql('test')
    .expect(screen.getByRole('textbox', {name: /value/i}).value)
    .eql('123')
    .expect(screen.getByRole('textbox', {name: /operation id/i}).value)
    .eql('5be8a137-fbb4-4c54-964c-9c7be98b80e6')
    .expect(screen.getByRole('checkbox', {name: 'Running Instances'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Finished Instances'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Completed'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Canceled'}).checked)
    .ok();

  // should navigate to dashboard and back, and see filters are still there

  await t.click(screen.getByRole('listitem', {name: 'Dashboard'}));
  await t.click(screen.getByRole('listitem', {name: 'Filters'}));

  await t
    .expect(
      screen.getByRole('combobox', {
        name: 'Workflow',
      }).value
    )
    .eql('processWithMultipleVersions')
    .expect(screen.getByRole('combobox', {name: 'Workflow Version'}).value)
    .eql('2')
    .expect(
      screen.getByRole('textbox', {
        name: 'Instance Id(s) separated by space or comma',
      }).value
    )
    .eql('2251799813685255')
    .expect(screen.getByRole('textbox', {name: /error message/i}).value)
    .eql('some error message')
    .expect(screen.getByRole('textbox', {name: /start date/i}).value)
    .eql('2020-09-10 18:41:44')
    .expect(screen.getByRole('textbox', {name: /end date/i}).value)
    .eql('2020-12-12 12:12:12')
    .expect(screen.getByRole('combobox', {name: /flow node/i}).value)
    .eql('alwaysFails')
    .expect(screen.getByRole('textbox', {name: /variable/i}).value)
    .eql('test')
    .expect(screen.getByRole('textbox', {name: /value/i}).value)
    .eql('123')
    .expect(screen.getByRole('textbox', {name: /operation id/i}).value)
    .eql('5be8a137-fbb4-4c54-964c-9c7be98b80e6')
    .expect(screen.getByRole('checkbox', {name: 'Running Instances'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Active'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Incidents'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Finished Instances'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Completed'}).checked)
    .ok()
    .expect(screen.getByRole('checkbox', {name: 'Canceled'}).checked)
    .ok();
});
