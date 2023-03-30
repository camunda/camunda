/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {getWrapper} from './mocks';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes';
import {processDiagramStore} from 'modules/stores/processDiagram';

import {Filters} from '../index';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';

jest.unmock('modules/utils/date/formatDate');

describe('Validations', () => {
  beforeAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess(mockProcessXML);

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

  it('should validate process instance keys', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/process instance key\(s\)/i), 'a');

    expect(
      await screen.findByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/process instance key\(s\)/i));

    await waitForElementToBeRemoved(() =>
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    );

    await user.type(screen.getByLabelText(/process instance key\(s\)/i), '1');

    expect(
      await screen.findByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/process instance key\(s\)/i));

    await waitForElementToBeRemoved(() =>
      screen.getByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    );
  });

  it('should validate Parent Process Instance Key', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Parent Process Instance Key'));
    await user.type(screen.getByLabelText(/Parent Process Instance Key/i), 'a');

    expect(
      await screen.findByText('Key has to be a 16 to 19 digit number')
    ).toBeInTheDocument();
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/Parent Process Instance Key/i));

    await waitForElementToBeRemoved(() =>
      screen.getByText('Key has to be a 16 to 19 digit number')
    );

    await user.type(screen.getByLabelText(/Parent Process Instance Key/i), '1');

    expect(
      await screen.findByText('Key has to be a 16 to 19 digit number')
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/Parent Process Instance Key/i));

    await waitForElementToBeRemoved(() =>
      screen.getByText('Key has to be a 16 to 19 digit number')
    );

    await user.type(
      screen.getByLabelText(/Parent Process Instance Key/i),
      '1111111111111111, 2222222222222222'
    );

    expect(
      await screen.findByText('Key has to be a 16 to 19 digit number')
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/Parent Process Instance Key/i));

    await waitForElementToBeRemoved(() =>
      screen.getByText('Key has to be a 16 to 19 digit number')
    );
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
      await screen.findByText('Name has to be filled')
    ).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/value/i));
    await user.type(screen.getByLabelText(/value/i), 'somethingInvalid');

    expect(
      await screen.findByText('Name has to be filled')
    ).toBeInTheDocument();

    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();

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
      await screen.findByText('Value has to be filled')
    ).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByTestId('optional-filter-variable-name'));

    await waitForElementToBeRemoved(() =>
      screen.getByText('Value has to be filled')
    );

    await user.type(screen.getByLabelText(/value/i), 'invalidValue');

    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();
    expect(
      await screen.findByText('Name has to be filled')
    ).toBeInTheDocument();
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.type(
      screen.getByTestId('optional-filter-variable-name'),
      'aRandomVariable'
    );

    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();

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

    expect(await screen.findByText('Id has to be a UUID')).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/operation id/i));

    expect(screen.queryByTitle('Id has to be a UUID')).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/operation id/i), 'a');

    expect(await screen.findByText('Id has to be a UUID')).toBeInTheDocument();
  });
});
