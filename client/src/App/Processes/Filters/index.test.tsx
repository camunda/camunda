/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Route, MemoryRouter, Routes} from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
  within,
} from '@testing-library/react';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {Filters} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {processesStore} from 'modules/stores/processes';
import {processInstancesDiagramStore} from 'modules/stores/processInstancesDiagram';
import {mockProcessXML} from 'modules/testUtils';
import {processInstancesVisibleFiltersStore} from 'modules/stores/processInstancesVisibleFilters';
import {LocationLog} from 'modules/utils/LocationLog';

type OptionalFilter =
  | 'Variable'
  | 'Instance Id(s)'
  | 'Operation Id'
  | 'Parent Instance Id'
  | 'Error Message'
  | 'Start Date'
  | 'End Date';

const displayOptionalFilter = (filterName: OptionalFilter) => {
  userEvent.click(screen.getByText(/^more filters$/i));
  userEvent.click(
    within(screen.getByTestId('more-filters-dropdown')).getByText(filterName)
  );
};

const validateFieldRemovedFromDropdown = (filterName: string) => {
  userEvent.click(screen.getByText(/^more filters$/i));
  expect(
    // eslint-disable-next-line testing-library/prefer-presence-queries
    within(screen.getByTestId('more-filters-dropdown')).queryByText(filterName)
  ).not.toBeInTheDocument();
};

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

function getWrapper(initialPath: string = '/') {
  const MockApp: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/" element={children} />
          </Routes>
          <LocationLog />
        </MemoryRouter>
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
    processInstancesDiagramStore.fetchProcessXml('bigVarProcess');

    jest.useFakeTimers();
  });

  afterEach(() => {
    processesStore.reset();
    processInstancesDiagramStore.reset();
    processInstancesVisibleFiltersStore.reset();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should load the process and version fields', async () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(screen.getByTestId('filter-process-name')).toBeEnabled()
    );

    userEvent.selectOptions(screen.getByTestId('filter-process-name'), [
      'Big variable process',
    ]);

    expect(screen.getByTestId('filter-process-version')).toBeEnabled();
    expect(screen.getByText('Version 1')).toBeInTheDocument();

    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent(
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
        `/?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`
      ),
    });

    expect(await screen.findByText('Big variable process')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByTestId('filter-process-name')).toBeEnabled()
    );

    expect(await screen.findByText('Version 1')).toBeInTheDocument();

    expect(await screen.findByText(MOCK_PARAMS.flowNodeId)).toBeInTheDocument();

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
    expect(screen.getByTestId(/active/)).toBeChecked();
    expect(screen.getByTestId(/incidents/)).toBeChecked();
    expect(screen.getByTestId(/completed/)).toBeChecked();
    expect(screen.getByTestId(/canceled/)).toBeChecked();
  });

  it('should set modified values to the URL', async () => {
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
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(screen.getByTestId('filter-process-name')).toBeEnabled()
    );
    await waitFor(() =>
      expect(screen.getByTestId('filter-flow-node')).toBeEnabled()
    );

    expect(screen.getByTestId('filter-process-name')).toHaveValue('');
    expect(screen.getByTestId('filter-process-version')).toHaveValue('all');
    expect(screen.getByTestId('filter-flow-node')).toHaveValue('');
    expect(screen.getByTestId(/active/)).not.toBeChecked();
    expect(screen.getByTestId(/incidents/)).not.toBeChecked();
    expect(screen.getByTestId(/completed/)).not.toBeChecked();
    expect(screen.getByTestId(/canceled/)).not.toBeChecked();

    userEvent.selectOptions(screen.getByTestId('filter-process-name'), [
      'Big variable process',
    ]);

    displayOptionalFilter('Instance Id(s)');
    userEvent.paste(screen.getByTestId('filter-instance-ids'), MOCK_VALUES.ids);

    displayOptionalFilter('Parent Instance Id');
    userEvent.paste(
      screen.getByTestId('filter-parent-instance-id'),
      MOCK_VALUES.parentInstanceId
    );

    displayOptionalFilter('Error Message');
    userEvent.paste(
      screen.getByTestId('filter-error-message'),
      MOCK_VALUES.errorMessage
    );

    displayOptionalFilter('Start Date');
    userEvent.paste(
      screen.getByTestId('filter-start-date'),
      MOCK_VALUES.startDate
    );

    displayOptionalFilter('End Date');
    userEvent.paste(screen.getByTestId('filter-end-date'), MOCK_VALUES.endDate);

    userEvent.selectOptions(screen.getByTestId('filter-flow-node'), [
      MOCK_VALUES.flowNodeId,
    ]);

    displayOptionalFilter('Variable');
    userEvent.paste(
      screen.getByTestId('filter-variable-name'),
      MOCK_VALUES.variableName
    );
    userEvent.paste(
      screen.getByTestId('filter-variable-value'),
      MOCK_VALUES.variableValue
    );

    displayOptionalFilter('Operation Id');
    userEvent.paste(
      screen.getByTestId('filter-operation-id'),
      MOCK_VALUES.operationId
    );

    userEvent.click(screen.getByTestId(/active/));
    userEvent.click(screen.getByTestId(/incidents/));
    userEvent.click(screen.getByTestId(/completed/));
    userEvent.click(screen.getByTestId(/canceled/));

    await waitFor(() =>
      expect(
        Object.fromEntries(
          new URLSearchParams(
            screen.getByTestId('search').textContent ?? ''
          ).entries()
        )
      ).toEqual(expect.objectContaining(MOCK_VALUES))
    );
  });

  it('should have JSON editor for variable value filter', async () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });

    displayOptionalFilter('Variable');
    userEvent.click(screen.getByTitle(/open json editor modal/i));

    expect(
      within(screen.getByTestId('modal')).getByRole('button', {
        name: /close/i,
      })
    ).toBeEnabled();
    expect(
      within(screen.getByTestId('modal')).getByRole('button', {name: /save/i})
    ).toBeEnabled();
    expect(
      within(screen.getByTestId('modal')).getByTestId('json-editor-container')
    ).toBeInTheDocument();
  });

  describe('Validations', () => {
    it('should validate instance ids', async () => {
      render(<Filters />, {
        wrapper: getWrapper(),
      });

      expect(screen.getByTestId('search')).toHaveTextContent('');

      displayOptionalFilter('Instance Id(s)');
      userEvent.type(screen.getByTestId('filter-instance-ids'), 'a');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();
      expect(screen.getByTestId('search')).toHaveTextContent('');

      userEvent.clear(screen.getByTestId('filter-instance-ids'));

      await waitForElementToBeRemoved(() =>
        screen.queryByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      );

      userEvent.type(screen.getByTestId('filter-instance-ids'), '1');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.clear(screen.getByTestId('filter-instance-ids'));

      await waitForElementToBeRemoved(() =>
        screen.queryByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      );
    });

    it('should validate parent instance id', async () => {
      render(<Filters />, {
        wrapper: getWrapper(),
      });
      expect(screen.getByTestId('search')).toHaveTextContent('');

      displayOptionalFilter('Parent Instance Id');
      userEvent.type(screen.getByTestId('filter-parent-instance-id'), 'a');

      expect(
        await screen.findByText('Id has to be a 16 to 19 digit number')
      ).toBeInTheDocument();
      expect(screen.getByTestId('search')).toHaveTextContent('');

      userEvent.clear(screen.getByTestId('filter-parent-instance-id'));

      await waitForElementToBeRemoved(() =>
        screen.getByText('Id has to be a 16 to 19 digit number')
      );

      userEvent.type(screen.getByTestId('filter-parent-instance-id'), '1');

      expect(
        await screen.findByText('Id has to be a 16 to 19 digit number')
      ).toBeInTheDocument();

      userEvent.clear(screen.getByTestId('filter-parent-instance-id'));

      await waitForElementToBeRemoved(() =>
        screen.getByText('Id has to be a 16 to 19 digit number')
      );

      userEvent.type(
        screen.getByTestId('filter-parent-instance-id'),
        '1111111111111111, 2222222222222222'
      );

      expect(
        await screen.findByText('Id has to be a 16 to 19 digit number')
      ).toBeInTheDocument();

      userEvent.clear(screen.getByTestId('filter-parent-instance-id'));

      await waitForElementToBeRemoved(() =>
        screen.getByText('Id has to be a 16 to 19 digit number')
      );
    });

    it('should validate start date', async () => {
      render(<Filters />, {
        wrapper: getWrapper(),
      });
      expect(screen.getByTestId('search')).toHaveTextContent('');

      displayOptionalFilter('Start Date');
      userEvent.type(screen.getByTestId('filter-start-date'), 'a');

      expect(
        await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();

      expect(screen.getByTestId('search')).toHaveTextContent('');

      userEvent.clear(screen.getByTestId('filter-start-date'));

      await waitForElementToBeRemoved(() =>
        screen.getByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      );

      userEvent.type(screen.getByTestId('filter-start-date'), '2021-05');

      expect(
        await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();
    });

    it('should validate end date', async () => {
      render(<Filters />, {
        wrapper: getWrapper(),
      });

      expect(screen.getByTestId('search')).toHaveTextContent('');

      displayOptionalFilter('End Date');
      userEvent.type(screen.getByTestId('filter-end-date'), 'a');

      expect(
        await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();

      expect(screen.getByTestId('search')).toHaveTextContent('');

      userEvent.clear(screen.getByTestId('filter-end-date'));

      await waitForElementToBeRemoved(() =>
        screen.getByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      );

      userEvent.type(screen.getByTestId('filter-end-date'), '2021-05');

      expect(
        await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();
    });

    it('should validate variable name', async () => {
      render(<Filters />, {
        wrapper: getWrapper(),
      });
      expect(screen.getByTestId('search')).toHaveTextContent('');

      displayOptionalFilter('Variable');

      userEvent.type(
        screen.getByTestId('filter-variable-value'),
        '"someValidValue"'
      );

      expect(
        await screen.findByText('Variable has to be filled')
      ).toBeInTheDocument();

      expect(screen.getByTestId('search')).toHaveTextContent('');

      userEvent.clear(screen.getByTestId('filter-variable-value'));
      userEvent.type(
        screen.getByTestId('filter-variable-value'),
        'somethingInvalid'
      );

      expect(
        await screen.findByText('Variable has to be filled')
      ).toBeInTheDocument();

      expect(
        await screen.findByText('Value has to be JSON')
      ).toBeInTheDocument();

      expect(screen.getByTestId('search')).toHaveTextContent('');
    });

    it('should validate variable value', async () => {
      render(<Filters />, {
        wrapper: getWrapper(),
      });
      expect(screen.getByTestId('search')).toHaveTextContent('');

      displayOptionalFilter('Variable');

      userEvent.type(
        screen.getByTestId('filter-variable-name'),
        'aRandomVariable'
      );

      expect(
        await screen.findByText('Value has to be JSON')
      ).toBeInTheDocument();

      expect(screen.getByTestId('search')).toHaveTextContent('');

      userEvent.clear(screen.getByTestId('filter-variable-name'));

      await waitForElementToBeRemoved(() =>
        screen.queryByText('Value has to be JSON')
      );

      userEvent.type(
        screen.getByTestId('filter-variable-value'),
        'invalidValue'
      );

      expect(
        await screen.findByText('Value has to be JSON')
      ).toBeInTheDocument();
      expect(
        await screen.findByText('Variable has to be filled')
      ).toBeInTheDocument();
      expect(screen.getByTestId('search')).toHaveTextContent('');

      userEvent.type(
        screen.getByTestId('filter-variable-name'),
        'aRandomVariable'
      );

      expect(
        await screen.findByText('Value has to be JSON')
      ).toBeInTheDocument();

      expect(screen.getByTestId('search')).toHaveTextContent('');
    });

    it('should validate operation id', async () => {
      render(<Filters />, {
        wrapper: getWrapper(),
      });
      expect(screen.getByTestId('search')).toHaveTextContent('');

      displayOptionalFilter('Operation Id');

      userEvent.type(screen.getByTestId('filter-operation-id'), 'g');

      expect(
        await screen.findByText('Id has to be a UUID')
      ).toBeInTheDocument();

      expect(screen.getByTestId('search')).toHaveTextContent('');

      userEvent.clear(screen.getByTestId('filter-operation-id'));

      expect(
        screen.queryByTitle('Id has to be a UUID')
      ).not.toBeInTheDocument();

      userEvent.type(screen.getByTestId('filter-operation-id'), 'a');

      expect(
        await screen.findByText('Id has to be a UUID')
      ).toBeInTheDocument();
    });
  });

  it('should enable the reset button', async () => {
    render(<Filters />, {
      wrapper: getWrapper('/?active=true&incidents=true'),
    });

    expect(screen.getByTitle(/reset filters/i)).toBeDisabled();

    userEvent.click(screen.getByTestId(/incidents/));

    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent('?active=true')
    );

    expect(screen.getByTitle(/reset filters/i)).toBeEnabled();
  });

  it('should not submit an invalid form after deleting an optional filter', async () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(screen.getByTestId('search')).toHaveTextContent('');

    displayOptionalFilter('Start Date');
    userEvent.type(screen.getByTestId('filter-start-date'), 'a');

    expect(
      await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
    ).toBeInTheDocument();

    expect(screen.getByTestId('search')).toHaveTextContent('');
    displayOptionalFilter('End Date');
    userEvent.click(screen.getByTestId('delete-endDate'));

    expect(screen.getByTestId('search')).toHaveTextContent('');
  });

  it('should be able to submit form after deleting an invalid optional filter', async () => {
    render(<Filters />, {
      wrapper: getWrapper('/?active=true&incidents=true'),
    });
    expect(screen.getByTestId('search')).toHaveTextContent(
      '?active=true&incidents=true'
    );

    displayOptionalFilter('Start Date');
    userEvent.type(screen.getByTestId('filter-start-date'), 'a');

    expect(
      await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
    ).toBeInTheDocument();

    expect(screen.getByTestId('search')).toHaveTextContent(
      '?active=true&incidents=true'
    );

    userEvent.click(screen.getByTestId('delete-startDate'));

    displayOptionalFilter('Error Message');
    userEvent.type(screen.getByTestId('filter-error-message'), 'test');

    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent(
        '?active=true&incidents=true&errorMessage=test'
      )
    );
  });

  describe('Interaction with other fields during validation', () => {
    it('validation for Instance IDs field should not affect other fields validation errors', async () => {
      render(<Filters />, {
        wrapper: getWrapper(),
      });

      displayOptionalFilter('Operation Id');
      userEvent.type(screen.getByTestId('filter-operation-id'), 'a');

      expect(
        await screen.findByText('Id has to be a UUID')
      ).toBeInTheDocument();

      displayOptionalFilter('Instance Id(s)');

      userEvent.type(screen.getByTestId('filter-instance-ids'), '1');

      expect(screen.getByText('Id has to be a UUID')).toBeInTheDocument();

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      expect(screen.getByText('Id has to be a UUID')).toBeInTheDocument();
    });

    it('validation for Operation ID field should not affect other fields validation errors', async () => {
      render(<Filters />, {
        wrapper: getWrapper(),
      });

      displayOptionalFilter('Instance Id(s)');
      userEvent.type(screen.getByTestId('filter-instance-ids'), '1');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      displayOptionalFilter('Operation Id');
      userEvent.type(screen.getByTestId('filter-operation-id'), 'abc');

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      expect(
        await screen.findByText('Id has to be a UUID')
      ).toBeInTheDocument();

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();
    });

    it('validation for Start Date field should not affect other fields validation errors', async () => {
      render(<Filters />, {
        wrapper: getWrapper(),
      });

      displayOptionalFilter('Instance Id(s)');
      userEvent.type(screen.getByTestId('filter-instance-ids'), '1');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      displayOptionalFilter('Start Date');
      userEvent.type(screen.getByTestId('filter-start-date'), '2021');

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      expect(
        await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();
    });

    it('validation for End Date field should not affect other fields validation errors', async () => {
      render(<Filters />, {
        wrapper: getWrapper(),
      });

      displayOptionalFilter('Instance Id(s)');
      userEvent.type(screen.getByTestId('filter-instance-ids'), '1');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      displayOptionalFilter('End Date');
      userEvent.type(screen.getByTestId('filter-end-date'), 'a');

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      expect(
        await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();
    });

    it('validation for Variable Value field should not affect other fields validation errors', async () => {
      render(<Filters />, {
        wrapper: getWrapper(),
      });

      displayOptionalFilter('Instance Id(s)');
      userEvent.type(screen.getByTestId('filter-instance-ids'), '1');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      displayOptionalFilter('Variable');
      userEvent.type(screen.getByTestId('filter-variable-value'), 'a');

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      expect(
        await screen.findByText('Variable has to be filled')
      ).toBeInTheDocument();

      expect(
        await screen.findByText('Value has to be JSON')
      ).toBeInTheDocument();

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();
    });

    it('validation for Variable Name field should not affect other fields validation errors', async () => {
      render(<Filters />, {
        wrapper: getWrapper(),
      });

      displayOptionalFilter('Instance Id(s)');
      userEvent.type(screen.getByTestId('filter-instance-ids'), '1');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      displayOptionalFilter('Variable');
      userEvent.type(screen.getByTestId('filter-variable-name'), 'a');

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      expect(
        await screen.findByText('Value has to be JSON')
      ).toBeInTheDocument();

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();
    });

    it('validation for Process, Version and Flow Node fields should not affect other fields validation errors', async () => {
      render(<Filters />, {
        wrapper: getWrapper(),
      });

      displayOptionalFilter('Instance Id(s)');
      userEvent.type(screen.getByTestId('filter-instance-ids'), '1');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.selectOptions(screen.getByTestId('filter-process-name'), [
        'complexProcess',
      ]);

      expect(screen.getByTestId('filter-process-version')).toBeEnabled();

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.selectOptions(screen.getByTestId('filter-process-version'), [
        'Version 2',
      ]);

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.selectOptions(screen.getByTestId('filter-flow-node'), [
        'ServiceTask_0kt6c5i',
      ]);

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();
    });

    it('clicking checkboxes should not affect other fields validation errors', async () => {
      render(<Filters />, {
        wrapper: getWrapper(),
      });

      displayOptionalFilter('Instance Id(s)');
      userEvent.type(screen.getByTestId('filter-instance-ids'), '1');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.click(screen.getByTestId(/active/));

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.click(screen.getByTestId(/incidents/));

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.click(screen.getByTestId(/completed/));

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.click(screen.getByTestId(/canceled/));

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.click(screen.getByTestId('filter-running-instances'));

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      userEvent.click(screen.getByTestId('filter-finished-instances'));

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();
    });

    it('should continue validation on blur', async () => {
      render(<Filters />, {
        wrapper: getWrapper(),
      });

      displayOptionalFilter('Start Date');
      displayOptionalFilter('End Date');

      userEvent.type(screen.getByTestId('filter-start-date'), '2021');

      userEvent.type(screen.getByTestId('filter-end-date'), '2021');

      await waitFor(() =>
        expect(
          screen.queryAllByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
        ).toHaveLength(2)
      );
    });
    describe('Optional Filters', () => {
      it('should initially hide optional filters', async () => {
        render(<Filters />, {
          wrapper: getWrapper(),
        });
        expect(
          screen.queryByTestId('filter-variable-name')
        ).not.toBeInTheDocument();
        expect(
          screen.queryByTestId('filter-variable-value')
        ).not.toBeInTheDocument();
        expect(
          screen.queryByTestId('filter-instance-ids')
        ).not.toBeInTheDocument();
        expect(
          screen.queryByTestId('filter-operation-id')
        ).not.toBeInTheDocument();
        expect(
          screen.queryByTestId('filter-parent-instance-id')
        ).not.toBeInTheDocument();
        expect(
          screen.queryByTestId('filter-error-message')
        ).not.toBeInTheDocument();
        expect(
          screen.queryByTestId('filter-start-date')
        ).not.toBeInTheDocument();
        expect(screen.queryByTestId('filter-end-date')).not.toBeInTheDocument();
      });

      it('should display variable fields on click', async () => {
        render(<Filters />, {
          wrapper: getWrapper(),
        });
        displayOptionalFilter('Variable');

        expect(screen.getByTestId('filter-variable-name')).toBeInTheDocument();
        expect(screen.getByTestId('filter-variable-value')).toBeInTheDocument();
        validateFieldRemovedFromDropdown('Variable');
      });

      it('should display instance ids field on click', async () => {
        render(<Filters />, {
          wrapper: getWrapper(),
        });
        displayOptionalFilter('Instance Id(s)');

        expect(screen.getByTestId('filter-instance-ids')).toBeInTheDocument();
        validateFieldRemovedFromDropdown('Instance Id(s)');
      });

      it('should display operation id field on click', async () => {
        render(<Filters />, {
          wrapper: getWrapper(),
        });
        displayOptionalFilter('Operation Id');

        expect(screen.getByTestId('filter-operation-id')).toBeInTheDocument();
        validateFieldRemovedFromDropdown('Operation Id');
      });

      it('should display parent instance id field on click', async () => {
        render(<Filters />, {
          wrapper: getWrapper(),
        });
        displayOptionalFilter('Parent Instance Id');

        expect(
          screen.getByTestId('filter-parent-instance-id')
        ).toBeInTheDocument();
        validateFieldRemovedFromDropdown('Parent Instance Id');
      });

      it('should display error message field on click', async () => {
        render(<Filters />, {
          wrapper: getWrapper(),
        });
        displayOptionalFilter('Error Message');

        expect(screen.getByTestId('filter-error-message')).toBeInTheDocument();
        validateFieldRemovedFromDropdown('Error Message');
      });

      it('should display start date field on click', async () => {
        render(<Filters />, {
          wrapper: getWrapper(),
        });
        displayOptionalFilter('Start Date');

        expect(screen.getByTestId('filter-start-date')).toBeInTheDocument();
        validateFieldRemovedFromDropdown('Start Date');
      });

      it('should display end date field on click', async () => {
        render(<Filters />, {
          wrapper: getWrapper(),
        });
        displayOptionalFilter('End Date');

        expect(screen.getByTestId('filter-end-date')).toBeInTheDocument();
        validateFieldRemovedFromDropdown('End Date');
      });

      it('should hide more filters button when all optional filters are visible', async () => {
        render(<Filters />, {
          wrapper: getWrapper(),
        });

        expect(screen.getByText(/^more filters$/i)).toBeInTheDocument();
        displayOptionalFilter('Variable');
        displayOptionalFilter('Instance Id(s)');
        displayOptionalFilter('Operation Id');
        displayOptionalFilter('Parent Instance Id');
        displayOptionalFilter('Error Message');
        displayOptionalFilter('Start Date');
        displayOptionalFilter('End Date');

        expect(
          screen.queryByTestId('more-filters-dropdown')
        ).not.toBeInTheDocument();

        userEvent.click(screen.getByTestId('delete-variable'));

        expect(screen.getByText(/^more filters$/i)).toBeInTheDocument();
      });

      it('should delete optional filters', async () => {
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
          variableValue: '"bar"',
          operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
          active: 'true',
          incidents: 'true',
          completed: 'true',
          canceled: 'true',
        } as const;

        render(<Filters />, {
          wrapper: getWrapper(
            `/?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`
          ),
        });

        expect(screen.getByTestId('search')).toHaveTextContent(
          `?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`
        );

        expect(screen.getByTestId('filter-instance-ids')).toBeInTheDocument();
        expect(
          screen.getByTestId('filter-parent-instance-id')
        ).toBeInTheDocument();
        expect(screen.getByTestId('filter-error-message')).toBeInTheDocument();
        expect(screen.getByTestId('filter-start-date')).toBeInTheDocument();
        expect(screen.getByTestId('filter-end-date')).toBeInTheDocument();
        expect(screen.getByTestId('filter-variable-name')).toBeInTheDocument();
        expect(screen.getByTestId('filter-variable-value')).toBeInTheDocument();
        expect(screen.getByTestId('filter-operation-id')).toBeInTheDocument();

        userEvent.click(screen.getByTestId('delete-ids'));

        await waitFor(() =>
          expect(screen.getByTestId('search')).toHaveTextContent(
            `?${new URLSearchParams(
              Object.entries({
                process: 'bigVarProcess',
                version: '1',
                parentInstanceId: '1954699813693756',
                errorMessage: 'a random error',
                startDate: '2021-02-21 18:17:18',
                endDate: '2021-02-23 18:17:18',
                flowNodeId: 'ServiceTask_0kt6c5i',
                variableName: 'foo',
                variableValue: '"bar"',
                operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
                active: 'true',
                incidents: 'true',
                completed: 'true',
                canceled: 'true',
              })
            ).toString()}`
          )
        );

        expect(
          screen.queryByTestId('filter-instance-ids')
        ).not.toBeInTheDocument();

        userEvent.click(screen.getByTestId('delete-parentInstanceId'));

        await waitFor(() =>
          expect(screen.getByTestId('search')).toHaveTextContent(
            `?${new URLSearchParams(
              Object.entries({
                process: 'bigVarProcess',
                version: '1',
                errorMessage: 'a random error',
                startDate: '2021-02-21 18:17:18',
                endDate: '2021-02-23 18:17:18',
                flowNodeId: 'ServiceTask_0kt6c5i',
                variableName: 'foo',
                variableValue: '"bar"',
                operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
                active: 'true',
                incidents: 'true',
                completed: 'true',
                canceled: 'true',
              })
            ).toString()}`
          )
        );
        expect(
          screen.queryByTestId('filter-parent-instance-id')
        ).not.toBeInTheDocument();

        userEvent.click(screen.getByTestId('delete-errorMessage'));

        await waitFor(() =>
          expect(screen.getByTestId('search')).toHaveTextContent(
            `?${new URLSearchParams(
              Object.entries({
                process: 'bigVarProcess',
                version: '1',
                startDate: '2021-02-21 18:17:18',
                endDate: '2021-02-23 18:17:18',
                flowNodeId: 'ServiceTask_0kt6c5i',
                variableName: 'foo',
                variableValue: '"bar"',
                operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
                active: 'true',
                incidents: 'true',
                completed: 'true',
                canceled: 'true',
              })
            ).toString()}`
          )
        );
        expect(
          screen.queryByTestId('filter-error-message')
        ).not.toBeInTheDocument();

        userEvent.click(screen.getByTestId('delete-startDate'));

        await waitFor(() =>
          expect(screen.getByTestId('search')).toHaveTextContent(
            `?${new URLSearchParams(
              Object.entries({
                process: 'bigVarProcess',
                version: '1',
                endDate: '2021-02-23 18:17:18',
                flowNodeId: 'ServiceTask_0kt6c5i',
                variableName: 'foo',
                variableValue: '"bar"',
                operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
                active: 'true',
                incidents: 'true',
                completed: 'true',
                canceled: 'true',
              })
            ).toString()}`
          )
        );
        expect(
          screen.queryByTestId('filter-start-date')
        ).not.toBeInTheDocument();

        userEvent.click(screen.getByTestId('delete-endDate'));

        await waitFor(() =>
          expect(screen.getByTestId('search')).toHaveTextContent(
            `?${new URLSearchParams(
              Object.entries({
                process: 'bigVarProcess',
                version: '1',
                flowNodeId: 'ServiceTask_0kt6c5i',
                variableName: 'foo',
                variableValue: '"bar"',
                operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
                active: 'true',
                incidents: 'true',
                completed: 'true',
                canceled: 'true',
              })
            ).toString()}`
          )
        );
        expect(screen.queryByTestId('filter-end-date')).not.toBeInTheDocument();

        userEvent.click(screen.getByTestId('delete-variable'));

        await waitFor(() =>
          expect(screen.getByTestId('search')).toHaveTextContent(
            `?${new URLSearchParams(
              Object.entries({
                process: 'bigVarProcess',
                version: '1',
                flowNodeId: 'ServiceTask_0kt6c5i',
                operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
                active: 'true',
                incidents: 'true',
                completed: 'true',
                canceled: 'true',
              })
            ).toString()}`
          )
        );
        expect(
          screen.queryByTestId('filter-variable-name')
        ).not.toBeInTheDocument();
        expect(
          screen.queryByTestId('filter-variable-value')
        ).not.toBeInTheDocument();

        userEvent.click(screen.getByTestId('delete-operationId'));

        await waitFor(() =>
          expect(screen.getByTestId('search')).toHaveTextContent(
            `?${new URLSearchParams(
              Object.entries({
                process: 'bigVarProcess',
                version: '1',
                flowNodeId: 'ServiceTask_0kt6c5i',
                active: 'true',
                incidents: 'true',
                completed: 'true',
                canceled: 'true',
              })
            ).toString()}`
          )
        );
        expect(
          screen.queryByTestId('filter-operation-id')
        ).not.toBeInTheDocument();
      });

      it('should remove optional filters on filter reset', async () => {
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
          variableValue: '"bar"',
          operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
          active: 'true',
          incidents: 'true',
          completed: 'true',
          canceled: 'true',
        } as const;

        render(<Filters />, {
          wrapper: getWrapper(
            `/?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`
          ),
        });

        expect(screen.getByTestId('search')).toHaveTextContent(
          `?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`
        );

        expect(screen.getByTestId('filter-instance-ids')).toBeInTheDocument();
        expect(
          screen.getByTestId('filter-parent-instance-id')
        ).toBeInTheDocument();
        expect(screen.getByTestId('filter-error-message')).toBeInTheDocument();
        expect(screen.getByTestId('filter-start-date')).toBeInTheDocument();
        expect(screen.getByTestId('filter-end-date')).toBeInTheDocument();
        expect(screen.getByTestId('filter-variable-name')).toBeInTheDocument();
        expect(screen.getByTestId('filter-variable-value')).toBeInTheDocument();
        expect(screen.getByTestId('filter-operation-id')).toBeInTheDocument();

        userEvent.click(screen.getByTitle(/reset filters/i));

        await waitFor(() =>
          expect(screen.getByTestId('search')).toHaveTextContent(
            '?active=true&incidents=true'
          )
        );

        expect(
          screen.queryByTestId('filter-instance-ids')
        ).not.toBeInTheDocument();
        expect(
          screen.queryByTestId('filter-parent-instance-id')
        ).not.toBeInTheDocument();
        expect(
          screen.queryByTestId('filter-error-message')
        ).not.toBeInTheDocument();
        expect(
          screen.queryByTestId('filter-start-date')
        ).not.toBeInTheDocument();
        expect(screen.queryByTestId('filter-end-date')).not.toBeInTheDocument();
        expect(
          screen.queryByTestId('filter-variable-name')
        ).not.toBeInTheDocument();
        expect(
          screen.queryByTestId('filter-variable-value')
        ).not.toBeInTheDocument();
        expect(
          screen.queryByTestId('filter-operation-id')
        ).not.toBeInTheDocument();
      });
    });
  });
});
