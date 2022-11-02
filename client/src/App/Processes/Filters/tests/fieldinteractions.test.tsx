/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {getWrapper} from './mocks';
import {IS_DATE_RANGE_FILTERS_ENABLED} from 'modules/feature-flags';

import {Filters} from '../index';
import {mockServer} from 'modules/mock-server/node';
import {rest} from 'msw';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes';
import {processDiagramStore} from 'modules/stores/processDiagram';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';

describe('Interaction with other fields during validation', () => {
  beforeEach(async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    processesStore.fetchProcesses();

    await processDiagramStore.fetchProcessDiagram('bigVarProcess');
    jest.useFakeTimers();
  });

  afterEach(() => {
    processesStore.reset();
    processDiagramStore.reset();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('validation for Instance IDs field should not affect other fields validation errors', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Operation Id'));
    await user.type(screen.getByLabelText(/operation id/i), 'a');

    expect(await screen.findByText('Id has to be a UUID')).toBeInTheDocument();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Process Instance Key(s)'));

    await user.type(screen.getByLabelText(/process instance key\(s\)/i), '1');

    expect(screen.getByText('Id has to be a UUID')).toBeInTheDocument();

    expect(
      await screen.findByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    expect(screen.getByText('Id has to be a UUID')).toBeInTheDocument();
  });

  it('validation for Operation ID field should not affect other fields validation errors', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/process instance key\(s\)/i), '1');

    expect(
      await screen.findByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Operation Id'));
    await user.type(screen.getByLabelText(/operation id/i), 'abc');

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    expect(await screen.findByText('Id has to be a UUID')).toBeInTheDocument();

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();
  });

  (IS_DATE_RANGE_FILTERS_ENABLED ? it.skip : it)(
    'validation for Start Date field should not affect other fields validation errors',
    async () => {
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Process Instance Key(s)'));
      await user.type(screen.getByLabelText(/process instance key\(s\)/i), '1');

      expect(
        await screen.findByText(
          'Key has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Start Date'));
      await user.type(screen.getByLabelText(/start date/i), '2021');

      expect(
        screen.getByText(
          'Key has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      expect(
        await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();

      expect(
        screen.getByText(
          'Key has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();
    }
  );

  (IS_DATE_RANGE_FILTERS_ENABLED ? it.skip : it)(
    'validation for End Date field should not affect other fields validation errors',
    async () => {
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Process Instance Key(s)'));
      await user.type(screen.getByLabelText(/process instance key\(s\)/i), '1');

      expect(
        await screen.findByText(
          'Key has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('End Date'));
      await user.type(screen.getByLabelText(/end date/i), 'a');

      expect(
        screen.getByText(
          'Key has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();

      expect(
        await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();

      expect(
        screen.getByText(
          'Key has to be a 16 to 19 digit number, separated by space or comma'
        )
      ).toBeInTheDocument();
    }
  );

  it('validation for Variable Value field should not affect other fields validation errors', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/process instance key\(s\)/i), '1');

    expect(
      await screen.findByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Variable'));
    await user.type(screen.getByLabelText(/value/i), 'a');

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    expect(
      await screen.findByText('Name has to be filled')
    ).toBeInTheDocument();

    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();
  });

  it('validation for Variable Name field should not affect other fields validation errors', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/process instance key\(s\)/i), '1');

    expect(
      await screen.findByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Variable'));
    await user.type(screen.getByTestId('optional-filter-variable-name'), 'a');

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    expect(
      await screen.findByText('Value has to be filled')
    ).toBeInTheDocument();

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();
  });

  it('validation for Process, Version and Flow Node fields should not affect other fields validation errors', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/process instance key\(s\)/i), '1');

    expect(
      await screen.findByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.selectOptions(screen.getByTestId('filter-process-name'), [
      'eventBasedGatewayProcess',
    ]);

    expect(screen.getByTestId('filter-process-version')).toBeEnabled();

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.selectOptions(screen.getByTestId('filter-process-version'), [
      '2',
    ]);

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.selectOptions(screen.getByTestId('filter-flow-node'), [
      'ServiceTask_0kt6c5i',
    ]);

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();
  });

  it('clicking checkboxes should not affect other fields validation errors', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/process instance key\(s\)/i), '1');

    expect(
      await screen.findByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByTestId(/active/));

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByTestId(/incidents/));

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByTestId(/completed/));

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByTestId(/canceled/));

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByTestId('filter-running-instances'));

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByTestId('filter-finished-instances'));

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();
  });

  it('should continue validation on blur', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Operation Id'));
    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Parent Process Instance Key'));

    await user.type(screen.getByLabelText(/operation id/i), '1');

    await user.type(screen.getByLabelText(/parent process instance key/i), '1');

    await waitFor(() =>
      expect(
        screen.getByText('Key has to be a 16 to 19 digit number')
      ).toBeInTheDocument()
    );

    await waitFor(() =>
      expect(screen.getByText('Id has to be a UUID')).toBeInTheDocument()
    );
  });
});
