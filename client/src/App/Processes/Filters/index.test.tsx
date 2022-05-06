/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {render, screen, waitFor, within} from 'modules/testing-library';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {Filters} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {processesStore} from 'modules/stores/processes';
import {processInstancesDiagramStore} from 'modules/stores/processInstancesDiagram';
import {mockProcessXML} from 'modules/testUtils';
import {LocationLog} from 'modules/utils/LocationLog';

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
  const MockApp: React.FC<{children?: React.ReactNode}> = ({children}) => {
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

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should load the process and version fields', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(screen.getByTestId('filter-process-name')).toBeEnabled()
    );

    await user.selectOptions(screen.getByTestId('filter-process-name'), [
      'Big variable process',
    ]);

    expect(screen.getByTestId('filter-process-version')).toBeEnabled();
    expect(screen.getByText('Version 1')).toBeInTheDocument();

    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?process=bigVarProcess&version=1$/
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
    const {user} = render(<Filters />, {
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

    await user.selectOptions(screen.getByTestId('filter-process-name'), [
      'Big variable process',
    ]);

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Instance Id(s)'));
    await user.type(
      screen.getByLabelText(/instance id\(s\)/i),
      MOCK_VALUES.ids
    );

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Parent Instance Id'));
    await user.type(
      screen.getByLabelText(/parent instance id/i),
      MOCK_VALUES.parentInstanceId
    );

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Error Message'));
    await user.type(
      screen.getByLabelText(/error message/i),
      MOCK_VALUES.errorMessage
    );

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Start Date'));
    await user.type(
      screen.getByLabelText(/start date/i),
      MOCK_VALUES.startDate
    );

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('End Date'));
    await user.type(screen.getByLabelText(/end date/i), MOCK_VALUES.endDate);

    await user.selectOptions(screen.getByTestId('filter-flow-node'), [
      MOCK_VALUES.flowNodeId,
    ]);

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Variable'));
    await user.type(
      screen.getByTestId('optional-filter-variable-name'),
      MOCK_VALUES.variableName
    );
    await user.type(screen.getByLabelText(/value/i), MOCK_VALUES.variableValue);

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Operation Id'));
    await user.type(
      screen.getByLabelText(/operation id/i),

      MOCK_VALUES.operationId
    );

    await user.click(screen.getByTestId(/active/));
    await user.click(screen.getByTestId(/incidents/));
    await user.click(screen.getByTestId(/completed/));
    await user.click(screen.getByTestId(/canceled/));

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
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Variable'));
    await user.click(screen.getByTitle(/open json editor modal/i));

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
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });

      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Instance Id(s)'));
      await user.type(screen.getByLabelText(/instance id\(s\)/i), 'a');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();
      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.clear(screen.getByLabelText(/instance id\(s\)/i));

      expect(
        screen.queryByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).not.toBeInTheDocument();

      await user.type(screen.getByLabelText(/instance id\(s\)/i), '1');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      await user.clear(screen.getByLabelText(/instance id\(s\)/i));

      expect(
        screen.queryByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).not.toBeInTheDocument();
    });

    it('should validate parent instance id', async () => {
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });
      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Parent Instance Id'));
      await user.type(screen.getByLabelText(/parent instance id/i), 'a');

      expect(
        await screen.findByText('Id has to be a 16 to 19 digit number')
      ).toBeInTheDocument();
      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.clear(screen.getByLabelText(/parent instance id/i));

      expect(
        screen.queryByText('Id has to be a 16 to 19 digit number')
      ).not.toBeInTheDocument();

      await user.type(screen.getByLabelText(/parent instance id/i), '1');

      expect(
        await screen.findByText('Id has to be a 16 to 19 digit number')
      ).toBeInTheDocument();

      await user.clear(screen.getByLabelText(/parent instance id/i));

      expect(
        screen.queryByText('Id has to be a 16 to 19 digit number')
      ).not.toBeInTheDocument();

      await user.type(
        screen.getByLabelText(/parent instance id/i),
        '1111111111111111, 2222222222222222'
      );

      expect(
        await screen.findByText('Id has to be a 16 to 19 digit number')
      ).toBeInTheDocument();

      await user.clear(screen.getByLabelText(/parent instance id/i));

      expect(
        screen.queryByText('Id has to be a 16 to 19 digit number')
      ).not.toBeInTheDocument();
    });

    it('should validate start date', async () => {
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });
      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Start Date'));
      await user.type(screen.getByLabelText(/start date/i), 'a');

      expect(
        await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();

      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.clear(screen.getByLabelText(/start date/i));

      expect(
        screen.queryByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).not.toBeInTheDocument();

      await user.type(screen.getByLabelText(/start date/i), '2021-05');

      expect(
        await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();
    });

    it('should validate end date', async () => {
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });

      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('End Date'));
      await user.type(screen.getByLabelText(/end date/i), 'a');

      expect(
        await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();

      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.clear(screen.getByLabelText(/end date/i));

      expect(
        screen.queryByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).not.toBeInTheDocument();

      await user.type(screen.getByLabelText(/end date/i), '2021-05');

      expect(
        await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();
    });

    it('should validate variable name', async () => {
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });
      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Variable'));

      await user.type(screen.getByLabelText(/value/i), '"someValidValue"');

      expect(
        await screen.findByText('Variable has to be filled')
      ).toBeInTheDocument();

      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.clear(screen.getByLabelText(/value/i));
      await user.type(screen.getByLabelText(/value/i), 'somethingInvalid');

      expect(
        await screen.findByText('Variable has to be filled')
      ).toBeInTheDocument();

      expect(
        await screen.findByText('Value has to be JSON')
      ).toBeInTheDocument();

      expect(screen.getByTestId('search')).toBeEmptyDOMElement();
    });

    it('should validate variable value', async () => {
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });
      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Variable'));

      await user.type(
        screen.getByTestId('optional-filter-variable-name'),
        'aRandomVariable'
      );

      expect(
        await screen.findByText('Value has to be JSON')
      ).toBeInTheDocument();

      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.clear(screen.getByTestId('optional-filter-variable-name'));

      expect(
        screen.queryByText('Value has to be JSON')
      ).not.toBeInTheDocument();

      await user.type(screen.getByLabelText(/value/i), 'invalidValue');

      expect(
        await screen.findByText('Value has to be JSON')
      ).toBeInTheDocument();
      expect(
        await screen.findByText('Variable has to be filled')
      ).toBeInTheDocument();
      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.type(
        screen.getByTestId('optional-filter-variable-name'),
        'aRandomVariable'
      );

      expect(
        await screen.findByText('Value has to be JSON')
      ).toBeInTheDocument();

      expect(screen.getByTestId('search')).toBeEmptyDOMElement();
    });

    it('should validate operation id', async () => {
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });
      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Operation Id'));

      await user.type(screen.getByLabelText(/operation id/i), 'g');

      expect(
        await screen.findByText('Id has to be a UUID')
      ).toBeInTheDocument();

      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.clear(screen.getByLabelText(/operation id/i));

      expect(
        screen.queryByTitle('Id has to be a UUID')
      ).not.toBeInTheDocument();

      await user.type(screen.getByLabelText(/operation id/i), 'a');

      expect(
        await screen.findByText('Id has to be a UUID')
      ).toBeInTheDocument();
    });
  });

  it('should enable the reset button', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper('/?active=true&incidents=true'),
    });

    expect(screen.getByTitle(/reset filters/i)).toBeDisabled();

    await user.click(screen.getByTestId(/incidents/));

    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent(/^\?active=true$/)
    );

    expect(screen.getByTitle(/reset filters/i)).toBeEnabled();
  });

  it('should not submit an invalid form after deleting an optional filter', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Start Date'));
    await user.type(screen.getByLabelText(/start date/i), 'a');

    expect(
      await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
    ).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();
    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('End Date'));
    await user.click(screen.getByTestId('delete-endDate'));

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();
  });

  it('should be able to submit form after deleting an invalid optional filter', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper('/?active=true&incidents=true'),
    });
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?active=true&incidents=true$/
    );

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Start Date'));
    await user.type(screen.getByLabelText(/start date/i), 'a');

    expect(
      await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
    ).toBeInTheDocument();

    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?active=true&incidents=true$/
    );

    await user.click(screen.getByTestId('delete-startDate'));

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Error Message'));
    await user.type(screen.getByLabelText(/error message/i), 'test');

    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?active=true&incidents=true&errorMessage=test$/
      )
    );
  });

  describe('Interaction with other fields during validation', () => {
    it('validation for Instance IDs field should not affect other fields validation errors', async () => {
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Operation Id'));
      await user.type(screen.getByLabelText(/operation id/i), 'a');

      expect(
        await screen.findByText('Id has to be a UUID')
      ).toBeInTheDocument();

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Instance Id(s)'));

      await user.type(screen.getByLabelText(/instance id\(s\)/i), '1');

      expect(screen.getByText('Id has to be a UUID')).toBeInTheDocument();

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      expect(screen.getByText('Id has to be a UUID')).toBeInTheDocument();
    });

    it('validation for Operation ID field should not affect other fields validation errors', async () => {
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Instance Id(s)'));
      await user.type(screen.getByLabelText(/instance id\(s\)/i), '1');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Operation Id'));
      await user.type(screen.getByLabelText(/operation id/i), 'abc');

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
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Instance Id(s)'));
      await user.type(screen.getByLabelText(/instance id\(s\)/i), '1');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Start Date'));
      await user.type(screen.getByLabelText(/start date/i), '2021');

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
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Instance Id(s)'));
      await user.type(screen.getByLabelText(/instance id\(s\)/i), '1');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('End Date'));
      await user.type(screen.getByLabelText(/end date/i), 'a');

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
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Instance Id(s)'));
      await user.type(screen.getByLabelText(/instance id\(s\)/i), '1');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Variable'));
      await user.type(screen.getByLabelText(/value/i), 'a');

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
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Instance Id(s)'));
      await user.type(screen.getByLabelText(/instance id\(s\)/i), '1');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Variable'));
      await user.type(screen.getByTestId('optional-filter-variable-name'), 'a');

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
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Instance Id(s)'));
      await user.type(screen.getByLabelText(/instance id\(s\)/i), '1');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      await user.selectOptions(screen.getByTestId('filter-process-name'), [
        'complexProcess',
      ]);

      expect(screen.getByTestId('filter-process-version')).toBeEnabled();

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      await user.selectOptions(screen.getByTestId('filter-process-version'), [
        'Version 2',
      ]);

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      await user.selectOptions(screen.getByTestId('filter-flow-node'), [
        'ServiceTask_0kt6c5i',
      ]);

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();
    });

    it('clicking checkboxes should not affect other fields validation errors', async () => {
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Instance Id(s)'));
      await user.type(screen.getByLabelText(/instance id\(s\)/i), '1');

      expect(
        await screen.findByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      await user.click(screen.getByTestId(/active/));

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      await user.click(screen.getByTestId(/incidents/));

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      await user.click(screen.getByTestId(/completed/));

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      await user.click(screen.getByTestId(/canceled/));

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      await user.click(screen.getByTestId('filter-running-instances'));

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      await user.click(screen.getByTestId('filter-finished-instances'));

      expect(
        screen.getByText(
          'Id has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();
    });

    it('should continue validation on blur', async () => {
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Start Date'));
      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('End Date'));

      await user.type(screen.getByLabelText(/start date/i), '2021');

      await user.type(screen.getByLabelText(/end date/i), '2021');

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
          screen.queryByTestId('optional-filter-variable-name')
        ).not.toBeInTheDocument();
        expect(screen.queryByLabelText(/value/i)).not.toBeInTheDocument();
        expect(
          screen.queryByLabelText(/instance id\(s\)/i)
        ).not.toBeInTheDocument();
        expect(
          screen.queryByLabelText(/operation id/i)
        ).not.toBeInTheDocument();
        expect(
          screen.queryByLabelText(/parent instance id/i)
        ).not.toBeInTheDocument();
        expect(
          screen.queryByLabelText(/error message/i)
        ).not.toBeInTheDocument();
        expect(screen.queryByLabelText(/start date/i)).not.toBeInTheDocument();
        expect(screen.queryByLabelText(/end date/i)).not.toBeInTheDocument();
      });

      it('should display variable fields on click', async () => {
        const {user} = render(<Filters />, {
          wrapper: getWrapper(),
        });

        await user.click(screen.getByText(/^more filters$/i));
        await user.click(screen.getByText('Variable'));

        expect(
          screen.getByTestId('optional-filter-variable-name')
        ).toBeInTheDocument();
        expect(screen.getByLabelText(/value/i)).toBeInTheDocument();
        await user.click(screen.getByText(/^more filters$/i));
        expect(
          // eslint-disable-next-line testing-library/prefer-presence-queries
          within(screen.getByTestId('more-filters-dropdown')).queryByText(
            'Variable'
          )
        ).not.toBeInTheDocument();
      });

      it('should display instance ids field on click', async () => {
        const {user} = render(<Filters />, {
          wrapper: getWrapper(),
        });

        await user.click(screen.getByText(/^more filters$/i));
        await user.click(screen.getByText('Instance Id(s)'));
        await user.click(screen.getByText(/^more filters$/i));

        expect(screen.getByLabelText(/instance id\(s\)/i)).toBeInTheDocument();
        expect(
          // eslint-disable-next-line testing-library/prefer-presence-queries
          within(screen.getByTestId('more-filters-dropdown')).queryByText(
            'Instance Id(s)'
          )
        ).not.toBeInTheDocument();
      });

      it('should display operation id field on click', async () => {
        const {user} = render(<Filters />, {
          wrapper: getWrapper(),
        });

        await user.click(screen.getByText(/^more filters$/i));
        await user.click(screen.getByText('Operation Id'));
        await user.click(screen.getByText(/^more filters$/i));

        expect(screen.getByLabelText(/operation id/i)).toBeInTheDocument();
        expect(
          // eslint-disable-next-line testing-library/prefer-presence-queries
          within(screen.getByTestId('more-filters-dropdown')).queryByText(
            'Operation Id'
          )
        ).not.toBeInTheDocument();
      });

      it('should display parent instance id field on click', async () => {
        const {user} = render(<Filters />, {
          wrapper: getWrapper(),
        });

        await user.click(screen.getByText(/^more filters$/i));
        await user.click(screen.getByText('Parent Instance Id'));
        await user.click(screen.getByText(/^more filters$/i));

        expect(
          screen.getByLabelText(/parent instance id/i)
        ).toBeInTheDocument();
        expect(
          // eslint-disable-next-line testing-library/prefer-presence-queries
          within(screen.getByTestId('more-filters-dropdown')).queryByText(
            'Parent Instance Id'
          )
        ).not.toBeInTheDocument();
      });

      it('should display error message field on click', async () => {
        const {user} = render(<Filters />, {
          wrapper: getWrapper(),
        });

        await user.click(screen.getByText(/^more filters$/i));
        await user.click(screen.getByText('Error Message'));
        await user.click(screen.getByText(/^more filters$/i));

        expect(screen.getByLabelText(/error message/i)).toBeInTheDocument();
        expect(
          // eslint-disable-next-line testing-library/prefer-presence-queries
          within(screen.getByTestId('more-filters-dropdown')).queryByText(
            'Error Message'
          )
        ).not.toBeInTheDocument();
      });

      it('should display start date field on click', async () => {
        const {user} = render(<Filters />, {
          wrapper: getWrapper(),
        });

        await user.click(screen.getByText(/^more filters$/i));
        await user.click(screen.getByText('Start Date'));
        await user.click(screen.getByText(/^more filters$/i));

        expect(screen.getByLabelText(/start date/i)).toBeInTheDocument();
        expect(
          // eslint-disable-next-line testing-library/prefer-presence-queries
          within(screen.getByTestId('more-filters-dropdown')).queryByText(
            'Start Date'
          )
        ).not.toBeInTheDocument();
      });

      it('should display end date field on click', async () => {
        const {user} = render(<Filters />, {
          wrapper: getWrapper(),
        });

        await user.click(screen.getByText(/^more filters$/i));
        await user.click(screen.getByText('End Date'));
        await user.click(screen.getByText(/^more filters$/i));

        expect(screen.getByLabelText(/end date/i)).toBeInTheDocument();
        expect(
          // eslint-disable-next-line testing-library/prefer-presence-queries
          within(screen.getByTestId('more-filters-dropdown')).queryByText(
            'End Date'
          )
        ).not.toBeInTheDocument();
      });

      it('should hide more filters button when all optional filters are visible', async () => {
        const {user} = render(<Filters />, {
          wrapper: getWrapper(),
        });

        await user.click(screen.getByText(/^more filters$/i));
        await user.click(screen.getByText('Variable'));
        await user.click(screen.getByText(/^more filters$/i));
        await user.click(screen.getByText('Instance Id(s)'));
        await user.click(screen.getByText(/^more filters$/i));
        await user.click(screen.getByText('Operation Id'));
        await user.click(screen.getByText(/^more filters$/i));
        await user.click(screen.getByText('Parent Instance Id'));
        await user.click(screen.getByText(/^more filters$/i));
        await user.click(screen.getByText('Error Message'));
        await user.click(screen.getByText(/^more filters$/i));
        await user.click(screen.getByText('Start Date'));
        await user.click(screen.getByText(/^more filters$/i));
        await user.click(screen.getByText('End Date'));

        expect(
          screen.queryByTestId('more-filters-dropdown')
        ).not.toBeInTheDocument();

        await user.click(screen.getByTestId('delete-variable'));

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

        const {user} = render(<Filters />, {
          wrapper: getWrapper(
            `/?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`
          ),
        });

        expect(screen.getByTestId('search').textContent).toBe(
          `?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`
        );

        expect(screen.getByLabelText(/instance id\(s\)/i)).toBeInTheDocument();
        expect(
          screen.getByLabelText(/parent instance id/i)
        ).toBeInTheDocument();
        expect(screen.getByLabelText(/error message/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/start date/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/end date/i)).toBeInTheDocument();
        expect(
          screen.getByTestId('optional-filter-variable-name')
        ).toBeInTheDocument();
        expect(screen.getByLabelText(/value/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/operation id/i)).toBeInTheDocument();

        await user.click(screen.getByTestId('delete-ids'));

        await waitFor(() =>
          expect(screen.getByTestId('search').textContent).toBe(
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
          screen.queryByLabelText(/instance id\(s\)/i)
        ).not.toBeInTheDocument();

        await user.click(screen.getByTestId('delete-parentInstanceId'));

        await waitFor(() =>
          expect(screen.getByTestId('search').textContent).toBe(
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
          screen.queryByLabelText(/parent instance id/i)
        ).not.toBeInTheDocument();

        await user.click(screen.getByTestId('delete-errorMessage'));

        await waitFor(() =>
          expect(screen.getByTestId('search').textContent).toBe(
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
          screen.queryByLabelText(/error message/i)
        ).not.toBeInTheDocument();

        await user.click(screen.getByTestId('delete-startDate'));

        await waitFor(() =>
          expect(screen.getByTestId('search').textContent).toBe(
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
        expect(screen.queryByLabelText(/start date/i)).not.toBeInTheDocument();

        await user.click(screen.getByTestId('delete-endDate'));

        await waitFor(() =>
          expect(screen.getByTestId('search').textContent).toBe(
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
        expect(screen.queryByLabelText(/end date/i)).not.toBeInTheDocument();

        await user.click(screen.getByTestId('delete-variable'));

        await waitFor(() =>
          expect(screen.getByTestId('search').textContent).toBe(
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
          screen.queryByTestId('optional-filter-variable-name')
        ).not.toBeInTheDocument();
        expect(screen.queryByLabelText(/value/i)).not.toBeInTheDocument();

        await user.click(screen.getByTestId('delete-operationId'));

        await waitFor(() =>
          expect(screen.getByTestId('search').textContent).toBe(
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
          screen.queryByLabelText(/operation id/i)
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

        const {user} = render(<Filters />, {
          wrapper: getWrapper(
            `/?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`
          ),
        });

        expect(screen.getByTestId('search').textContent).toBe(
          `?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`
        );

        expect(screen.getByLabelText(/instance id\(s\)/i)).toBeInTheDocument();
        expect(
          screen.getByLabelText(/parent instance id/i)
        ).toBeInTheDocument();
        expect(screen.getByLabelText(/error message/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/start date/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/end date/i)).toBeInTheDocument();
        expect(
          screen.getByTestId('optional-filter-variable-name')
        ).toBeInTheDocument();
        expect(screen.getByLabelText(/value/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/operation id/i)).toBeInTheDocument();

        await user.click(screen.getByTitle(/reset filters/i));

        await waitFor(() =>
          expect(screen.getByTestId('search')).toHaveTextContent(
            /^\?active=true&incidents=true$/
          )
        );

        expect(
          screen.queryByLabelText(/instance id\(s\)/i)
        ).not.toBeInTheDocument();
        expect(
          screen.queryByLabelText(/parent instance id/i)
        ).not.toBeInTheDocument();
        expect(
          screen.queryByLabelText(/error message/i)
        ).not.toBeInTheDocument();
        expect(screen.queryByLabelText(/start date/i)).not.toBeInTheDocument();
        expect(screen.queryByLabelText(/end date/i)).not.toBeInTheDocument();
        expect(
          screen.queryByTestId('optional-filter-variable-name')
        ).not.toBeInTheDocument();
        expect(screen.queryByLabelText(/value/i)).not.toBeInTheDocument();
        expect(
          screen.queryByLabelText(/operation id/i)
        ).not.toBeInTheDocument();
      });
    });
  });

  it('Should order optional filters', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper('/?active=true&incidents=true'),
    });

    const optionalFilters: Array<{name: string; fields: string[]}> = [
      {name: 'Error Message', fields: ['Error Message']},
      {name: 'Parent Instance Id', fields: ['Parent Instance Id']},
      {name: 'End Date', fields: ['End Date']},
      {name: 'Variable', fields: ['Name', 'Value']},
      {name: 'Start Date', fields: ['Start Date']},
      {name: 'Instance Id(s)', fields: ['Instance Id(s)']},
      {name: 'Operation Id', fields: ['Operation Id']},
    ];

    let fieldLabels = optionalFilters.reduce((acc, optionalFilter) => {
      return [...acc, ...optionalFilter.fields];
    }, [] as string[]);

    for (let i = 0; i < optionalFilters.length; i++) {
      await user.click(screen.getByText('More Filters'));
      await user.click(screen.getByText(optionalFilters[i]!.name));
    }

    let visibleOptionalFilters = screen.getAllByTestId(/^optional-filter-/i);

    for (let i = 0; i < visibleOptionalFilters.length; i++) {
      expect(
        within(visibleOptionalFilters[i]?.parentElement!).getByText(
          fieldLabels[i]!
        )
      ).toBeInTheDocument();
    }

    await user.click(screen.getByText(/reset filters/i));

    for (let i = 0; i < visibleOptionalFilters.length; i++) {
      expect(
        within(visibleOptionalFilters[i]?.parentElement!).queryByText(
          fieldLabels[i]!
        )
      ).not.toBeInTheDocument();
    }

    optionalFilters.reverse();
    fieldLabels = optionalFilters.reduce((acc, optionalFilter) => {
      return [...acc, ...optionalFilter.fields];
    }, [] as string[]);

    for (let i = 0; i < optionalFilters.length; i++) {
      await user.click(screen.getByText('More Filters'));
      await user.click(screen.getByText(optionalFilters[i]!.name));
    }

    visibleOptionalFilters = screen.getAllByTestId(/^optional-filter-/i);

    for (let i = 0; i < visibleOptionalFilters.length; i++) {
      expect(
        within(visibleOptionalFilters[i]?.parentElement!).getByText(
          fieldLabels[i]!
        )
      ).toBeInTheDocument();
    }
  });
});
