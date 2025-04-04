/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {getWrapper} from './mocks';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes/processes.list';

import {Filters} from '../index';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {ERRORS} from 'modules/validators';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

jest.unmock('modules/utils/date/formatDate');

describe('Validations', () => {
  beforeEach(async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    processesStore.fetchProcesses();

    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should validate process instance keys', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/^process instance key\(s\)$/i), 'a');

    expect(await screen.findByText(ERRORS.ids)).toBeInTheDocument();
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/^process instance key\(s\)$/i));

    expect(screen.queryByText(ERRORS.ids)).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/^process instance key\(s\)$/i), '1');

    expect(await screen.findByText(ERRORS.ids)).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/^process instance key\(s\)$/i));

    expect(screen.queryByText(ERRORS.ids)).not.toBeInTheDocument();
  });

  it('should validate Parent Process Instance Key', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Parent Process Instance Key'));
    await user.type(
      screen.getByLabelText(/^Parent Process Instance Key$/i),
      'a',
    );

    expect(
      await screen.findByText(ERRORS.parentInstanceId),
    ).toBeInTheDocument();
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/^Parent Process Instance Key$/i));

    expect(screen.queryByText(ERRORS.parentInstanceId)).not.toBeInTheDocument();

    await user.type(
      screen.getByLabelText(/^Parent Process Instance Key$/i),
      '1',
    );

    expect(
      await screen.findByText(ERRORS.parentInstanceId),
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/^Parent Process Instance Key$/i));

    expect(screen.queryByText(ERRORS.parentInstanceId)).not.toBeInTheDocument();

    await user.type(
      screen.getByLabelText(/^Parent Process Instance Key$/i),
      '1111111111111111, 2222222222222222',
    );

    expect(
      await screen.findByText(ERRORS.parentInstanceId),
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/^Parent Process Instance Key$/i));

    expect(screen.queryByText(ERRORS.parentInstanceId)).not.toBeInTheDocument();
  });

  it('should validate variable name', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Variable'));

    await user.type(screen.getByLabelText(/^value$/i), '"someValidValue"');

    expect(
      await screen.findByText(ERRORS.variables.nameUnfilled),
    ).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/^value$/i));
    await user.type(screen.getByLabelText(/^value$/i), 'somethingInvalid');

    expect(
      await screen.findByText(ERRORS.variables.nameUnfilled),
    ).toBeInTheDocument();

    expect(
      await screen.findByText(ERRORS.variables.valueInvalid),
    ).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();
  });

  it('should validate variable value', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Variable'));

    await user.type(
      screen.getByTestId('optional-filter-variable-name'),
      'aRandomVariable',
    );

    expect(
      await screen.findByText(ERRORS.variables.valueUnfilled),
    ).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByTestId('optional-filter-variable-name'));

    expect(
      screen.queryByText(ERRORS.variables.valueUnfilled),
    ).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/^value$/i), 'invalidValue');

    expect(
      await screen.findByText(ERRORS.variables.valueInvalid),
    ).toBeInTheDocument();
    expect(
      await screen.findByText(ERRORS.variables.nameUnfilled),
    ).toBeInTheDocument();
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.type(
      screen.getByTestId('optional-filter-variable-name'),
      'aRandomVariable',
    );

    expect(
      await screen.findByText(ERRORS.variables.valueInvalid),
    ).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();
  });

  it('should validate multiple variable values', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Variable'));
    await user.click(screen.getByLabelText('Multiple'));

    await user.type(
      screen.getByTestId('optional-filter-variable-name'),
      'aRandomVariable',
    );

    expect(
      await screen.findByText(ERRORS.variables.valueUnfilled),
    ).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByTestId('optional-filter-variable-name'));

    expect(
      screen.queryByText(ERRORS.variables.valueUnfilled),
    ).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/^values$/i), 'invalidValue');

    expect(
      await screen.findByText(ERRORS.variables.mulipleValueInvalid),
    ).toBeInTheDocument();
    expect(
      await screen.findByText(ERRORS.variables.nameUnfilled),
    ).toBeInTheDocument();
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.type(
      screen.getByTestId('optional-filter-variable-name'),
      'aRandomVariable',
    );

    expect(
      await screen.findByText(ERRORS.variables.mulipleValueInvalid),
    ).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();
  });

  it('should validate operation id', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Operation Id'));

    await user.type(screen.getByLabelText(/^operation id$/i), 'g');

    expect(await screen.findByText(ERRORS.operationId)).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/^operation id$/i));

    expect(screen.queryByTitle(ERRORS.operationId)).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/^operation id$/i), 'a');

    expect(await screen.findByText(ERRORS.operationId)).toBeInTheDocument();
  });
});
