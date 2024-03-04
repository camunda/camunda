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

import {
  render,
  screen,
  within,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {OperationsPanel} from './index';
import * as CONSTANTS from './constants';
import {mockOperationFinished, mockOperationRunning} from './index.setup';
import {MemoryRouter} from 'react-router-dom';
import {mockFetchBatchOperations} from 'modules/mocks/api/fetchBatchOperations';
jest.mock('modules/utils/localStorage', () => ({
  getStateLocally: () => ({
    isFiltersCollapsed: false,
    isOperationsCollapsed: false,
  }),
}));

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return <MemoryRouter>{children}</MemoryRouter>;
};

describe('OperationsPanel', () => {
  it('should display empty panel on mount', async () => {
    mockFetchBatchOperations().withSuccess([]);

    render(<OperationsPanel />, {wrapper: Wrapper});

    expect(
      await screen.findByText(CONSTANTS.EMPTY_MESSAGE),
    ).toBeInTheDocument();
  });

  it('should render skeleton when loading', async () => {
    mockFetchBatchOperations().withSuccess([]);

    render(<OperationsPanel />, {wrapper: Wrapper});

    expect(screen.getByTestId('skeleton')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('skeleton'));
  });

  it('should render operation entries', async () => {
    mockFetchBatchOperations().withSuccess([
      mockOperationRunning,
      mockOperationFinished,
    ]);

    render(<OperationsPanel />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.getByTestId('skeleton'));

    const [firstOperation, secondOperation] =
      screen.getAllByTestId('operations-entry');

    expect(firstOperation).toBeInTheDocument();
    expect(secondOperation).toBeInTheDocument();

    const withinFirstOperation = within(firstOperation!);
    const withinSecondOperation = within(secondOperation!);

    expect(
      withinFirstOperation.getByText(mockOperationRunning.id),
    ).toBeInTheDocument();
    expect(withinFirstOperation.getByText('Retry')).toBeInTheDocument();
    expect(
      withinFirstOperation.getByTestId('operation-retry-icon'),
    ).toBeInTheDocument();

    expect(
      withinSecondOperation.getByText(mockOperationFinished.id),
    ).toBeInTheDocument();
    expect(withinSecondOperation.getByText('Cancel')).toBeInTheDocument();
    expect(
      withinSecondOperation.getByTestId('operation-cancel-icon'),
    ).toBeInTheDocument();
  });

  it('should show an error message', async () => {
    const consoleErrorMock = jest
      .spyOn(global.console, 'error')
      .mockImplementation();

    mockFetchBatchOperations().withServerError();
    const {unmount} = render(<OperationsPanel />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Operations could not be fetched'),
    ).toBeInTheDocument();

    unmount();

    mockFetchBatchOperations().withNetworkError();

    render(<OperationsPanel />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Operations could not be fetched'),
    ).toBeInTheDocument();

    consoleErrorMock.mockRestore();
  });
});
