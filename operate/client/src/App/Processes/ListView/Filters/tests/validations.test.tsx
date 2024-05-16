/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {render, screen} from 'modules/testing-library';
import {getWrapper} from './mocks';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes/processes.list';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';

import {Filters} from '../index';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {ERRORS} from 'modules/validators';

jest.unmock('modules/utils/date/formatDate');

describe('Validations', () => {
  beforeEach(async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    processesStore.fetchProcesses();

    await processXmlStore.fetchProcessXml('bigVarProcess');
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
    await user.click(screen.getByLabelText(/multiple/i));

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
