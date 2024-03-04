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

import {Filters} from '../index';

import {groupedProcessesMock, mockProcessXML} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes/processes.list';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {
  selectFlowNode,
  selectProcess,
  selectProcessVersion,
} from 'modules/testUtils/selectComboBoxOption';
import {ERRORS} from 'modules/validators';

jest.unmock('modules/utils/date/formatDate');

describe('Interaction with other fields during validation', () => {
  beforeEach(async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    processesStore.fetchProcesses();

    await processXmlStore.fetchProcessXml('bigVarProcess');
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

    await selectFlowNode({user, option: 'ServiceTask_0kt6c5i'});

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
