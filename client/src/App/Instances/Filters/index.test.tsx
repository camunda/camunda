/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Router, Route} from 'react-router-dom';
import {History, createMemoryHistory} from 'history';
import userEvent from '@testing-library/user-event';
import {render, screen, waitFor} from '@testing-library/react';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {Filters} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {workflowsStore} from 'modules/stores/workflows';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {mockWorkflowXML} from 'modules/testUtils';

const GROUPED_WORKFLOWS = [
  {
    bpmnProcessId: 'bigVarProcess',
    name: 'Big variable process',
    workflows: [
      {
        id: '2251799813685530',
        name: 'Big variable process',
        version: 1,
        bpmnProcessId: 'bigVarProcess',
      },
    ],
  },
  {
    bpmnProcessId: 'complexProcess',
    name: null,
    workflows: [
      {
        id: '2251799813687825',
        name: null,
        version: 3,
        bpmnProcessId: 'complexProcess',
      },
      {
        id: '2251799813686926',
        name: null,
        version: 2,
        bpmnProcessId: 'complexProcess',
      },
      {
        id: '2251799813685540',
        name: null,
        version: 1,
        bpmnProcessId: 'complexProcess',
      },
    ],
  },
] as const;

function getWrapper(history: History = createMemoryHistory()) {
  const MockApp: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <Router history={history}>
          <Route path="/">{children}</Route>
        </Router>
      </ThemeProvider>
    );
  };

  return MockApp;
}

describe('Filters', () => {
  beforeEach(async () => {
    mockServer.use(
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockWorkflowXML))
      ),
      rest.get('/api/workflows/grouped', (_, res, ctx) =>
        res.once(ctx.json(GROUPED_WORKFLOWS))
      )
    );

    workflowsStore.fetchWorkflows();
    instancesDiagramStore.fetchWorkflowXml('bigVarProcess');
  });

  afterEach(() => {
    workflowsStore.reset();
    instancesDiagramStore.reset();
  });

  it('should load the workflow and version fields', async () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(screen.getByLabelText('Workflow')).toBeEnabled()
    );

    userEvent.selectOptions(screen.getByLabelText('Workflow'), [
      'Big variable process',
    ]);

    expect(screen.getByLabelText('Workflow Version')).toBeEnabled();
    expect(screen.getByDisplayValue('Version 1')).toBeInTheDocument();
  });

  it('should load values from the URL', async () => {
    const MOCK_PARAMS = {
      workflow: 'bigVarProcess',
      version: '1',
      ids: '2251799813685467',
      errorMessage: 'a random error',
      startDate: '2021-02-21 18:17:18',
      endDate: '2021-02-23 18:17:18',
      flowNodeId: 'ServiceTask_0kt6c5i',
      variableName: 'foo',
      variableValue: 'bar',
      operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
      active: 'true',
      incidents: 'true',
      completed: 'true',
      canceled: 'true',
    } as const;

    render(<Filters />, {
      wrapper: getWrapper(
        createMemoryHistory({
          initialEntries: [
            `/?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`,
          ],
        })
      ),
    });

    expect(
      await screen.findByDisplayValue('Big variable process')
    ).toBeInTheDocument();
    expect(await screen.findByDisplayValue('Version 1')).toBeInTheDocument();
    expect(
      await screen.findByDisplayValue(MOCK_PARAMS.flowNodeId)
    ).toBeInTheDocument();
    expect(screen.getByDisplayValue(MOCK_PARAMS.ids)).toBeInTheDocument();
    expect(
      screen.getByDisplayValue(MOCK_PARAMS.errorMessage)
    ).toBeInTheDocument();
    expect(screen.getByDisplayValue(MOCK_PARAMS.startDate)).toBeInTheDocument();
    expect(screen.getByDisplayValue(MOCK_PARAMS.endDate)).toBeInTheDocument();
    expect(
      screen.getByDisplayValue(MOCK_PARAMS.variableName)
    ).toBeInTheDocument();
    expect(
      screen.getByDisplayValue(MOCK_PARAMS.variableValue)
    ).toBeInTheDocument();
    expect(
      screen.getByDisplayValue(MOCK_PARAMS.operationId)
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Active')).toBeChecked();
    expect(screen.getByLabelText('Incidents')).toBeChecked();
    expect(screen.getByLabelText('Completed')).toBeChecked();
    expect(screen.getByLabelText('Canceled')).toBeChecked();
  });

  it('should set modified values to the URL', async () => {
    const MOCK_HISTORY = createMemoryHistory({
      initialEntries: ['/'],
    });
    const MOCK_VALUES = {
      workflow: 'bigVarProcess',
      version: '1',
      ids: '2251799813685462',
      errorMessage: 'an error',
      startDate: '2021-02-24 13:57:29',
      endDate: '2021-02-26 13:57:29',
      flowNodeId: 'ServiceTask_0kt6c5i',
      variableName: 'variableFoo',
      variableValue: 'true',
      operationId: '90fdfe82-090b-4d84-af31-5db612514191',
      active: 'true',
      incidents: 'true',
      completed: 'true',
      canceled: 'true',
    } as const;
    render(<Filters />, {
      wrapper: getWrapper(MOCK_HISTORY),
    });

    await waitFor(() =>
      expect(screen.getByLabelText('Workflow')).toBeEnabled()
    );
    await waitFor(() =>
      expect(screen.getByLabelText('Flow Node')).toBeEnabled()
    );

    expect(screen.getByLabelText('Workflow')).toHaveValue('');
    expect(screen.getByLabelText('Workflow Version')).toHaveValue('');
    expect(
      screen.getByLabelText('Instance Id(s) separated by space or comma')
    ).toHaveValue('');
    expect(screen.getByLabelText('Error Message')).toHaveValue('');
    expect(screen.getByLabelText('Start Date YYYY-MM-DD hh:mm:ss')).toHaveValue(
      ''
    );
    expect(screen.getByLabelText('End Date YYYY-MM-DD hh:mm:ss')).toHaveValue(
      ''
    );
    expect(screen.getByLabelText('Flow Node')).toHaveValue('');
    expect(screen.getByLabelText('Variable')).toHaveValue('');
    expect(screen.getByLabelText('Value')).toHaveValue('');
    expect(screen.getByLabelText('Operation Id')).toHaveValue('');
    expect(screen.getByLabelText('Active')).not.toBeChecked();
    expect(screen.getByLabelText('Incidents')).not.toBeChecked();
    expect(screen.getByLabelText('Completed')).not.toBeChecked();
    expect(screen.getByLabelText('Canceled')).not.toBeChecked();

    userEvent.selectOptions(screen.getByLabelText('Workflow'), [
      'Big variable process',
    ]);
    userEvent.type(
      screen.getByLabelText('Instance Id(s) separated by space or comma'),
      MOCK_VALUES.ids
    );
    userEvent.type(
      screen.getByLabelText('Error Message'),
      MOCK_VALUES.errorMessage
    );
    userEvent.type(
      screen.getByLabelText('Start Date YYYY-MM-DD hh:mm:ss'),
      MOCK_VALUES.startDate
    );
    userEvent.type(
      screen.getByLabelText('End Date YYYY-MM-DD hh:mm:ss'),
      MOCK_VALUES.endDate
    );
    userEvent.selectOptions(screen.getByLabelText('Flow Node'), [
      MOCK_VALUES.flowNodeId,
    ]);
    userEvent.type(screen.getByLabelText('Variable'), MOCK_VALUES.variableName);
    userEvent.type(screen.getByLabelText('Value'), MOCK_VALUES.variableValue);
    userEvent.type(
      screen.getByLabelText('Operation Id'),
      MOCK_VALUES.operationId
    );
    userEvent.click(screen.getByLabelText('Active'));
    userEvent.click(screen.getByLabelText('Incidents'));
    userEvent.click(screen.getByLabelText('Completed'));
    userEvent.click(screen.getByLabelText('Canceled'));

    expect(
      Object.fromEntries(
        new URLSearchParams(MOCK_HISTORY.location.search).entries()
      )
    ).toEqual(expect.objectContaining(MOCK_VALUES));
  });

  it('should validate fields', async () => {
    const MOCK_HISTORY = createMemoryHistory({
      initialEntries: ['/'],
    });

    render(<Filters />, {
      wrapper: getWrapper(MOCK_HISTORY),
    });

    expect(MOCK_HISTORY.location.search).toBe('');

    userEvent.type(
      screen.getByLabelText('Instance Id(s) separated by space or comma'),
      'a'
    );

    expect(
      await screen.findByTitle(
        'Id has to be 16 to 19 digit numbers, separated by space or comma'
      )
    ).toBeInTheDocument();
    expect(MOCK_HISTORY.location.search).toBe('');

    userEvent.clear(
      screen.getByLabelText('Instance Id(s) separated by space or comma')
    );

    expect(
      screen.queryByTitle(
        'Id has to be 16 to 19 digit numbers, separated by space or comma'
      )
    ).not.toBeInTheDocument();

    userEvent.type(
      screen.getByLabelText('Start Date YYYY-MM-DD hh:mm:ss'),
      'a'
    );

    expect(
      await screen.findByTitle('Date has to be in format YYYY-MM-DD hh:mm:ss')
    ).toBeInTheDocument();
    expect(MOCK_HISTORY.location.search).toBe('');

    userEvent.clear(screen.getByLabelText('Start Date YYYY-MM-DD hh:mm:ss'));

    expect(
      screen.queryByTitle('Date has to be in format YYYY-MM-DD hh:mm:ss')
    ).not.toBeInTheDocument();

    userEvent.type(screen.getByLabelText('End Date YYYY-MM-DD hh:mm:ss'), 'a');

    expect(
      await screen.findByTitle('Date has to be in format YYYY-MM-DD hh:mm:ss')
    ).toBeInTheDocument();
    expect(MOCK_HISTORY.location.search).toBe('');

    userEvent.clear(screen.getByLabelText('End Date YYYY-MM-DD hh:mm:ss'));

    expect(
      screen.queryByTitle('Date has to be in format YYYY-MM-DD hh:mm:ss')
    ).not.toBeInTheDocument();

    userEvent.type(screen.getByLabelText('Variable'), 'aRandomVariable');

    expect(
      await screen.findByTitle('Value has to be JSON')
    ).toBeInTheDocument();
    expect(MOCK_HISTORY.location.search).toBe('');

    userEvent.clear(screen.getByLabelText('Variable'));

    expect(screen.queryByTitle('Value has to be JSON')).not.toBeInTheDocument();

    userEvent.type(screen.getByLabelText('Value'), 'a');

    expect(
      await screen.findByTitle('Value has to be JSON')
    ).toBeInTheDocument();
    expect(MOCK_HISTORY.location.search).toBe('');

    userEvent.clear(screen.getByLabelText('Value'));

    expect(screen.queryByTitle('Value has to be JSON')).not.toBeInTheDocument();

    userEvent.type(screen.getByLabelText('Value'), 'true');

    expect(
      await screen.findByTitle('Variable has to be filled')
    ).toBeInTheDocument();
    expect(MOCK_HISTORY.location.search).toBe('');

    userEvent.clear(screen.getByLabelText('Value'));

    expect(
      screen.queryByTitle('Variable has to be filled')
    ).not.toBeInTheDocument();

    userEvent.type(screen.getByLabelText('Operation Id'), 'a');

    expect(await screen.findByTitle('Id has to be a UUID')).toBeInTheDocument();
    expect(MOCK_HISTORY.location.search).toBe('');

    userEvent.clear(screen.getByLabelText('Operation Id'));

    expect(screen.queryByTitle('Id has to be a UUID')).not.toBeInTheDocument();
  });

  it('should enable the reset button', async () => {
    const MOCK_HISTORY = createMemoryHistory({
      initialEntries: ['/?active=true&incidents=true'],
    });
    render(<Filters />, {
      wrapper: getWrapper(MOCK_HISTORY),
    });

    expect(
      screen.getByRole('button', {
        name: 'Reset Filters',
      })
    ).toBeDisabled();

    userEvent.click(screen.getByLabelText('Incidents'));

    expect(
      screen.getByRole('button', {
        name: 'Reset Filters',
      })
    ).toBeEnabled();
  });

  it('should submit without delay', async () => {
    const MOCK_HISTORY = createMemoryHistory({
      initialEntries: ['/'],
    });
    render(<Filters />, {
      wrapper: getWrapper(MOCK_HISTORY),
    });

    await waitFor(() =>
      expect(screen.getByLabelText('Workflow')).toBeEnabled()
    );
    await waitFor(() =>
      expect(screen.getByLabelText('Flow Node')).toBeEnabled()
    );

    userEvent.click(screen.getByLabelText('Active'));
    expect(MOCK_HISTORY.location.search).toBe('?active=true');

    userEvent.click(screen.getByLabelText('Incidents'));
    expect(MOCK_HISTORY.location.search).toBe('?active=true&incidents=true');

    userEvent.click(screen.getByLabelText('Completed'));
    expect(MOCK_HISTORY.location.search).toBe(
      '?active=true&incidents=true&completed=true'
    );

    userEvent.click(screen.getByLabelText('Canceled'));
    expect(MOCK_HISTORY.location.search).toBe(
      '?active=true&incidents=true&completed=true&canceled=true'
    );

    userEvent.selectOptions(screen.getByLabelText('Workflow'), [
      'complexProcess',
    ]);
    expect(MOCK_HISTORY.location.search).toBe(
      '?active=true&incidents=true&completed=true&canceled=true&workflow=complexProcess&version=3'
    );

    userEvent.selectOptions(screen.getByLabelText('Workflow Version'), [
      'Version 1',
    ]);
    expect(MOCK_HISTORY.location.search).toBe(
      '?active=true&incidents=true&completed=true&canceled=true&workflow=complexProcess&version=1'
    );

    userEvent.selectOptions(screen.getByLabelText('Flow Node'), [
      'ServiceTask_0kt6c5i',
    ]);
    expect(MOCK_HISTORY.location.search).toBe(
      '?active=true&incidents=true&completed=true&canceled=true&workflow=complexProcess&version=1&flowNodeId=ServiceTask_0kt6c5i'
    );
  });
});
