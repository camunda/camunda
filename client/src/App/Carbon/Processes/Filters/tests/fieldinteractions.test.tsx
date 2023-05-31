/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {getWrapper} from './mocks';

import {Filters} from '../index';

import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes';
import {processDiagramStore} from 'modules/stores/processDiagram';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {
  selectFlowNode,
  selectProcess,
  selectProcessVersion,
} from 'modules/testUtils/selectComboBoxOption';

jest.unmock('modules/utils/date/formatDate');

describe('Interaction with other fields during validation', () => {
  beforeEach(async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    processesStore.fetchProcesses();

    await processDiagramStore.fetchProcessDiagram('bigVarProcess');
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('validation for Instance IDs field should not affect other fields validation errors', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Operation Id'));
    await user.type(screen.getByLabelText(/^operation id$/i), 'a');

    expect(await screen.findByText('Id has to be a UUID')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Process Instance Key(s)'));

    await user.type(screen.getByLabelText(/^process instance key\(s\)$/i), '1');

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

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/^process instance key\(s\)$/i), '1');

    expect(
      await screen.findByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Operation Id'));
    await user.type(screen.getByLabelText(/^operation id$/i), 'abc');

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

  it('validation for Variable Value field should not affect other fields validation errors', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/^process instance key\(s\)$/i), '1');

    expect(
      await screen.findByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
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

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/^process instance key\(s\)$/i), '1');

    expect(
      await screen.findByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
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

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/^process instance key\(s\)$/i), '1');

    expect(
      await screen.findByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await selectProcess({user, option: 'eventBasedGatewayProcess'});
    expect(
      screen.getByLabelText('Version', {selector: 'button'})
    ).toBeEnabled();
    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await selectProcessVersion({user, option: '2'});

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await selectFlowNode({user, option: 'ServiceTask_0kt6c5i'});

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

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/^process instance key\(s\)$/i), '1');

    expect(
      await screen.findByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByLabelText(/^active$/i));

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByLabelText(/^incidents$/i));

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByLabelText(/^completed$/i));

    expect(
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.click(screen.getByLabelText(/^canceled$/i));

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

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Operation Id'));
    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Parent Process Instance Key'));

    await user.type(screen.getByLabelText(/^operation id$/i), '1');

    await user.type(
      screen.getByLabelText(/^parent process instance key$/i),
      '1'
    );

    expect(
      await screen.findByText('Key has to be a 16 to 19 digit number')
    ).toBeInTheDocument();

    expect(await screen.findByText('Id has to be a UUID')).toBeInTheDocument();
  });
});
