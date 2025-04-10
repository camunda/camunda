/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {getWrapper} from './mocks';

import {Filters} from '../index';

import {groupedProcessesMock, mockProcessXML} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes/processes.list';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {
  selectFlowNode,
  selectProcess,
  selectProcessVersion,
} from 'modules/testUtils/selectComboBoxOption';
import {ERRORS} from 'modules/validators';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

jest.unmock('modules/utils/date/formatDate');

describe('Interaction with other fields during validation', () => {
  beforeEach(async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    processesStore.fetchProcesses();

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

    expect(await screen.findByText(ERRORS.operationId)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Process Instance Key(s)'));

    await user.type(screen.getByLabelText(/^process instance key\(s\)$/i), '1');

    expect(screen.getByText(ERRORS.operationId)).toBeInTheDocument();

    expect(await screen.findByText(ERRORS.ids)).toBeInTheDocument();

    expect(screen.getByText(ERRORS.operationId)).toBeInTheDocument();
  });

  it('validation for Operation ID field should not affect other fields validation errors', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/^process instance key\(s\)$/i), '1');

    expect(await screen.findByText(ERRORS.ids)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Operation Id'));
    await user.type(screen.getByLabelText(/^operation id$/i), 'abc');

    expect(screen.getByText(ERRORS.ids)).toBeInTheDocument();

    expect(await screen.findByText(ERRORS.operationId)).toBeInTheDocument();

    expect(screen.getByText(ERRORS.ids)).toBeInTheDocument();
  });

  it('validation for Variable Value field should not affect other fields validation errors', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/^process instance key\(s\)$/i), '1');

    expect(await screen.findByText(ERRORS.ids)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Variable'));
    await user.type(screen.getByLabelText(/value/i), 'a');

    expect(screen.getByText(ERRORS.ids)).toBeInTheDocument();

    expect(
      await screen.findByText('Name has to be filled'),
    ).toBeInTheDocument();

    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();

    expect(screen.getByText(ERRORS.ids)).toBeInTheDocument();
  });

  it('validation for Variable Name field should not affect other fields validation errors', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/^process instance key\(s\)$/i), '1');

    expect(await screen.findByText(ERRORS.ids)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Variable'));
    await user.type(screen.getByTestId('optional-filter-variable-name'), 'a');

    expect(screen.getByText(ERRORS.ids)).toBeInTheDocument();

    expect(
      await screen.findByText('Value has to be filled'),
    ).toBeInTheDocument();

    expect(screen.getByText(ERRORS.ids)).toBeInTheDocument();
  });

  it('validation for Process, Version and Flow Node fields should not affect other fields validation errors', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/^process instance key\(s\)$/i), '1');

    expect(await screen.findByText(ERRORS.ids)).toBeInTheDocument();

    await selectProcess({user, option: 'eventBasedGatewayProcess'});
    expect(
      screen.getByLabelText('Version', {selector: 'button'}),
    ).toBeEnabled();
    expect(screen.getByText(ERRORS.ids)).toBeInTheDocument();

    await selectProcessVersion({user, option: '2'});

    expect(screen.getByText(ERRORS.ids)).toBeInTheDocument();

    await selectFlowNode({user, option: 'Service Task 1'});

    expect(screen.getByText(ERRORS.ids)).toBeInTheDocument();
  });

  it('clicking checkboxes should not affect other fields validation errors', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/^process instance key\(s\)$/i), '1');

    expect(await screen.findByText(ERRORS.ids)).toBeInTheDocument();

    await user.click(screen.getByLabelText(/^active$/i));

    expect(screen.getByText(ERRORS.ids)).toBeInTheDocument();

    await user.click(screen.getByLabelText(/^incidents$/i));

    expect(screen.getByText(ERRORS.ids)).toBeInTheDocument();

    await user.click(screen.getByLabelText(/^completed$/i));

    expect(screen.getByText(ERRORS.ids)).toBeInTheDocument();

    await user.click(screen.getByLabelText(/^canceled$/i));

    expect(screen.getByText(ERRORS.ids)).toBeInTheDocument();

    await user.click(screen.getByTestId('filter-running-instances'));

    expect(screen.getByText(ERRORS.ids)).toBeInTheDocument();

    await user.click(screen.getByTestId('filter-finished-instances'));

    expect(screen.getByText(ERRORS.ids)).toBeInTheDocument();
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
      '1',
    );

    expect(
      await screen.findByText('Key has to be a 16 to 19 digit number'),
    ).toBeInTheDocument();

    expect(await screen.findByText(ERRORS.operationId)).toBeInTheDocument();
  });
});
