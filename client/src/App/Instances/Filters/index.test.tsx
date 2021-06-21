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
import {processesStore} from 'modules/stores/processes';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {mockProcessXML} from 'modules/testUtils';

const GROUPED_PROCESSES = [
  {
    bpmnProcessId: 'bigVarProcess',
    name: 'Big variable process',
    processes: [
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
    processes: [
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
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      ),
      rest.get('/api/processes/grouped', (_, res, ctx) =>
        res.once(ctx.json(GROUPED_PROCESSES))
      )
    );

    processesStore.fetchProcesses();
    instancesDiagramStore.fetchProcessXml('bigVarProcess');
  });

  afterEach(() => {
    processesStore.reset();
    instancesDiagramStore.reset();
  });

  it('should load the process and version fields', async () => {
    const MOCK_HISTORY = createMemoryHistory({
      initialEntries: ['/'],
    });
    render(<Filters />, {
      wrapper: getWrapper(MOCK_HISTORY),
    });

    await waitFor(() => expect(screen.getByLabelText('Process')).toBeEnabled());

    userEvent.selectOptions(screen.getByLabelText('Process'), [
      'Big variable process',
    ]);

    expect(screen.getByLabelText('Process Version')).toBeEnabled();
    expect(screen.getByDisplayValue('Version 1')).toBeInTheDocument();

    await waitFor(() =>
      expect(MOCK_HISTORY.location.search).toBe(
        '?process=bigVarProcess&version=1'
      )
    );
  });

  it('should load values from the URL', async () => {
    const MOCK_PARAMS = {
      process: 'bigVarProcess',
      version: '1',
      ids: '2251799813685467',
      parentInstanceId: '1954699813693756',
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
    await waitFor(() => expect(screen.getByLabelText('Process')).toBeEnabled());

    expect(await screen.findByDisplayValue('Version 1')).toBeInTheDocument();
    expect(
      await screen.findByDisplayValue(MOCK_PARAMS.flowNodeId)
    ).toBeInTheDocument();
    expect(screen.getByDisplayValue(MOCK_PARAMS.ids)).toBeInTheDocument();

    expect(
      screen.getByDisplayValue(MOCK_PARAMS.parentInstanceId)
    ).toBeInTheDocument();

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
      process: 'bigVarProcess',
      version: '1',
      ids: '2251799813685462',
      parentInstanceId: '1954699813693756',
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

    await waitFor(() => expect(screen.getByLabelText('Process')).toBeEnabled());
    await waitFor(() =>
      expect(screen.getByLabelText('Flow Node')).toBeEnabled()
    );

    expect(screen.getByLabelText('Process')).toHaveValue('');
    expect(screen.getByLabelText('Process Version')).toHaveValue('');
    expect(
      screen.getByLabelText('Instance Id(s) separated by space or comma')
    ).toHaveValue('');
    expect(screen.getByLabelText('Parent Instance Id')).toHaveValue('');
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

    userEvent.selectOptions(screen.getByLabelText('Process'), [
      'Big variable process',
    ]);
    userEvent.paste(
      screen.getByLabelText('Instance Id(s) separated by space or comma'),
      MOCK_VALUES.ids
    );
    userEvent.paste(
      screen.getByLabelText('Parent Instance Id'),
      MOCK_VALUES.parentInstanceId
    );

    userEvent.paste(
      screen.getByLabelText('Error Message'),
      MOCK_VALUES.errorMessage
    );
    userEvent.paste(
      screen.getByLabelText('Start Date YYYY-MM-DD hh:mm:ss'),
      MOCK_VALUES.startDate
    );
    userEvent.paste(
      screen.getByLabelText('End Date YYYY-MM-DD hh:mm:ss'),
      MOCK_VALUES.endDate
    );
    userEvent.selectOptions(screen.getByLabelText('Flow Node'), [
      MOCK_VALUES.flowNodeId,
    ]);
    userEvent.paste(
      screen.getByLabelText('Variable'),
      MOCK_VALUES.variableName
    );
    userEvent.paste(screen.getByLabelText('Value'), MOCK_VALUES.variableValue);
    userEvent.paste(
      screen.getByLabelText('Operation Id'),
      MOCK_VALUES.operationId
    );
    userEvent.click(screen.getByLabelText('Active'));
    userEvent.click(screen.getByLabelText('Incidents'));
    userEvent.click(screen.getByLabelText('Completed'));
    userEvent.click(screen.getByLabelText('Canceled'));

    await waitFor(() =>
      expect(
        Object.fromEntries(
          new URLSearchParams(MOCK_HISTORY.location.search).entries()
        )
      ).toEqual(expect.objectContaining(MOCK_VALUES))
    );
  });

  describe('Validations', () => {
    it('should validate instance ids', async () => {
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
        screen.getByTitle(
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
        screen.getByLabelText('Instance Id(s) separated by space or comma'),
        '1'
      );

      expect(
        await screen.findByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.clear(
        screen.getByLabelText('Instance Id(s) separated by space or comma')
      );

      expect(
        screen.queryByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).not.toBeInTheDocument();
    });

    it('should validate start date', async () => {
      const MOCK_HISTORY = createMemoryHistory({
        initialEntries: ['/'],
      });

      render(<Filters />, {
        wrapper: getWrapper(MOCK_HISTORY),
      });
      expect(MOCK_HISTORY.location.search).toBe('');

      userEvent.type(
        screen.getByLabelText('Start Date YYYY-MM-DD hh:mm:ss'),
        'a'
      );

      expect(
        screen.getByTitle('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();

      expect(MOCK_HISTORY.location.search).toBe('');

      userEvent.clear(screen.getByLabelText('Start Date YYYY-MM-DD hh:mm:ss'));

      expect(
        screen.queryByTitle('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).not.toBeInTheDocument();

      userEvent.type(
        screen.getByLabelText('Start Date YYYY-MM-DD hh:mm:ss'),
        '2021-05'
      );

      expect(
        await screen.findByTitle('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();
    });

    it('should validate end date', async () => {
      const MOCK_HISTORY = createMemoryHistory({
        initialEntries: ['/'],
      });

      render(<Filters />, {
        wrapper: getWrapper(MOCK_HISTORY),
      });

      expect(MOCK_HISTORY.location.search).toBe('');

      userEvent.type(
        screen.getByLabelText('End Date YYYY-MM-DD hh:mm:ss'),
        'a'
      );

      expect(
        screen.getByTitle('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();

      expect(MOCK_HISTORY.location.search).toBe('');

      userEvent.clear(screen.getByLabelText('End Date YYYY-MM-DD hh:mm:ss'));

      expect(
        screen.queryByTitle('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).not.toBeInTheDocument();

      userEvent.type(
        screen.getByLabelText('Start Date YYYY-MM-DD hh:mm:ss'),
        '2021-05'
      );

      expect(
        await screen.findByTitle('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();
    });

    it('should validate variable name', async () => {
      const MOCK_HISTORY = createMemoryHistory({
        initialEntries: ['/'],
      });

      render(<Filters />, {
        wrapper: getWrapper(MOCK_HISTORY),
      });
      expect(MOCK_HISTORY.location.search).toBe('');

      userEvent.type(screen.getByLabelText('Value'), '"someValidValue"');

      expect(
        await screen.findByTitle('Variable has to be filled')
      ).toBeInTheDocument();

      expect(MOCK_HISTORY.location.search).toBe('');

      userEvent.clear(screen.getByLabelText('Value'));
      userEvent.type(screen.getByLabelText('Value'), 'somethingInvalid');

      expect(
        await screen.findByTitle(
          'Variable has to be filled and Value has to be JSON'
        )
      ).toBeInTheDocument();

      expect(MOCK_HISTORY.location.search).toBe('');
    });

    it('should validate variable value', async () => {
      const MOCK_HISTORY = createMemoryHistory({
        initialEntries: ['/'],
      });

      render(<Filters />, {
        wrapper: getWrapper(MOCK_HISTORY),
      });
      expect(MOCK_HISTORY.location.search).toBe('');

      userEvent.type(screen.getByLabelText('Variable'), 'aRandomVariable');

      expect(
        await screen.findByTitle('Value has to be JSON')
      ).toBeInTheDocument();

      expect(MOCK_HISTORY.location.search).toBe('');

      userEvent.clear(screen.getByLabelText('Variable'));

      expect(
        screen.queryByTitle('Value has to be JSON')
      ).not.toBeInTheDocument();

      userEvent.type(screen.getByLabelText('Value'), 'invalidValue');

      expect(
        await screen.findByTitle(
          'Variable has to be filled and Value has to be JSON'
        )
      ).toBeInTheDocument();
      expect(MOCK_HISTORY.location.search).toBe('');

      userEvent.type(screen.getByLabelText('Variable'), 'aRandomVariable');

      expect(
        await screen.findByTitle('Value has to be JSON')
      ).toBeInTheDocument();

      expect(MOCK_HISTORY.location.search).toBe('');
    });

    it('should validate operation id', async () => {
      const MOCK_HISTORY = createMemoryHistory({
        initialEntries: ['/'],
      });

      render(<Filters />, {
        wrapper: getWrapper(MOCK_HISTORY),
      });
      expect(MOCK_HISTORY.location.search).toBe('');

      userEvent.type(screen.getByLabelText('Operation Id'), 'g');

      expect(screen.getByTitle('Id has to be a UUID')).toBeInTheDocument();

      expect(MOCK_HISTORY.location.search).toBe('');

      userEvent.clear(screen.getByLabelText('Operation Id'));

      expect(
        screen.queryByTitle('Id has to be a UUID')
      ).not.toBeInTheDocument();

      userEvent.type(screen.getByLabelText('Operation Id'), 'a');

      expect(
        await screen.findByTitle('Id has to be a UUID')
      ).toBeInTheDocument();
    });
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

    await waitFor(() =>
      expect(MOCK_HISTORY.location.search).toBe('?active=true')
    );

    expect(
      screen.getByRole('button', {
        name: 'Reset Filters',
      })
    ).toBeEnabled();
  });

  describe('Interaction with other fields during validation', () => {
    beforeEach(() => {
      jest.useFakeTimers();
    });

    afterEach(() => {
      jest.clearAllTimers();
      jest.useRealTimers();
    });

    it('validation for Instance IDs field should not affect other fields validation errors', async () => {
      const MOCK_HISTORY = createMemoryHistory({
        initialEntries: ['/'],
      });

      render(<Filters />, {
        wrapper: getWrapper(MOCK_HISTORY),
      });

      userEvent.type(screen.getByLabelText('Operation Id'), 'a');

      expect(
        await screen.findByTitle('Id has to be a UUID')
      ).toBeInTheDocument();

      userEvent.type(
        screen.getByLabelText('Instance Id(s) separated by space or comma'),
        '1'
      );

      expect(screen.getByTitle('Id has to be a UUID')).toBeInTheDocument();

      expect(
        await screen.findByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      expect(screen.getByTitle('Id has to be a UUID')).toBeInTheDocument();
    });

    it('validation for Operation ID field should not affect other fields validation errors', async () => {
      const MOCK_HISTORY = createMemoryHistory({
        initialEntries: ['/'],
      });

      render(<Filters />, {
        wrapper: getWrapper(MOCK_HISTORY),
      });

      userEvent.type(
        screen.getByLabelText('Instance Id(s) separated by space or comma'),
        '1'
      );

      expect(
        await screen.findByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.type(screen.getByLabelText('Operation Id'), 'a');

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      expect(
        await screen.findByTitle('Id has to be a UUID')
      ).toBeInTheDocument();

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();
    });

    it('validation for Start Date field should not affect other fields validation errors', async () => {
      const MOCK_HISTORY = createMemoryHistory({
        initialEntries: ['/'],
      });

      render(<Filters />, {
        wrapper: getWrapper(MOCK_HISTORY),
      });

      userEvent.type(
        screen.getByLabelText('Instance Id(s) separated by space or comma'),
        '1'
      );

      expect(
        await screen.findByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.type(
        screen.getByLabelText('Start Date YYYY-MM-DD hh:mm:ss'),
        '2021'
      );

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      expect(
        await screen.findByTitle('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();
    });

    it('validation for End Date field should not affect other fields validation errors', async () => {
      const MOCK_HISTORY = createMemoryHistory({
        initialEntries: ['/'],
      });

      render(<Filters />, {
        wrapper: getWrapper(MOCK_HISTORY),
      });

      userEvent.type(
        screen.getByLabelText('Instance Id(s) separated by space or comma'),
        '1'
      );

      expect(
        await screen.findByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.type(
        screen.getByLabelText('End Date YYYY-MM-DD hh:mm:ss'),
        'a'
      );

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      expect(
        await screen.findByTitle('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();
    });

    it('validation for Variable Value field should not affect other fields validation errors', async () => {
      const MOCK_HISTORY = createMemoryHistory({
        initialEntries: ['/'],
      });

      render(<Filters />, {
        wrapper: getWrapper(MOCK_HISTORY),
      });

      userEvent.type(
        screen.getByLabelText('Instance Id(s) separated by space or comma'),
        '1'
      );

      expect(
        await screen.findByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.type(screen.getByLabelText('Value'), 'a');

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      expect(
        await screen.findByTitle(
          'Variable has to be filled and Value has to be JSON'
        )
      ).toBeInTheDocument();

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();
    });

    it('validation for Variable Name field should not affect other fields validation errors', async () => {
      const MOCK_HISTORY = createMemoryHistory({
        initialEntries: ['/'],
      });

      render(<Filters />, {
        wrapper: getWrapper(MOCK_HISTORY),
      });

      userEvent.type(
        screen.getByLabelText('Instance Id(s) separated by space or comma'),
        '1'
      );

      expect(
        await screen.findByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.type(screen.getByLabelText('Variable'), 'a');

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      expect(
        await screen.findByTitle('Value has to be JSON')
      ).toBeInTheDocument();

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();
    });

    it('validation for Process, Version and Flow Node fields should not affect other fields validation errors', async () => {
      const MOCK_HISTORY = createMemoryHistory({
        initialEntries: ['/'],
      });

      render(<Filters />, {
        wrapper: getWrapper(MOCK_HISTORY),
      });

      userEvent.type(
        screen.getByLabelText('Instance Id(s) separated by space or comma'),
        '1'
      );

      expect(
        await screen.findByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.selectOptions(screen.getByLabelText('Process'), [
        'complexProcess',
      ]);

      expect(screen.getByLabelText('Process Version')).toBeEnabled();

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.selectOptions(screen.getByLabelText('Process Version'), [
        'Version 2',
      ]);

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.selectOptions(screen.getByLabelText('Flow Node'), [
        'ServiceTask_0kt6c5i',
      ]);

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();
    });

    it('clicking checkboxes should not affect other fields validation errors', async () => {
      const MOCK_HISTORY = createMemoryHistory({
        initialEntries: ['/'],
      });

      render(<Filters />, {
        wrapper: getWrapper(MOCK_HISTORY),
      });

      userEvent.type(
        screen.getByLabelText('Instance Id(s) separated by space or comma'),
        '1'
      );

      expect(
        await screen.findByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.click(screen.getByLabelText('Active'));

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.click(screen.getByLabelText('Incidents'));

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.click(screen.getByLabelText('Completed'));

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.click(screen.getByLabelText('Canceled'));

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.click(screen.getByLabelText('Running Instances'));

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.click(screen.getByLabelText('Finished Instances'));

      expect(
        screen.getByTitle(
          'Id has to be 16 to 19 digit numbers, separated by space or comma'
        )
      ).toBeInTheDocument();
    });

    it('should continue validation on blur', async () => {
      render(<Filters />, {
        wrapper: getWrapper(),
      });

      userEvent.type(
        screen.getByLabelText('Start Date YYYY-MM-DD hh:mm:ss'),
        '2021'
      );

      userEvent.type(
        screen.getByLabelText('End Date YYYY-MM-DD hh:mm:ss'),
        '2021'
      );

      await waitFor(() =>
        expect(
          screen.queryAllByTitle('Date has to be in format YYYY-MM-DD hh:mm:ss')
        ).toHaveLength(2)
      );
    });
  });
});
