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
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
import {
  assignApproverGroup,
  assignApproverGroupWithoutVariables,
  invoiceClassification,
} from 'modules/mocks/mockDecisionInstance';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {InputsAndOutputs} from './index';
import {mockFetchDecisionInstance} from 'modules/mocks/api/decisionInstances/fetchDecisionInstance';
import {useEffect} from 'react';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return decisionInstanceDetailsStore.reset;
  }, []);

  return <>{children}</>;
};

describe('<InputsAndOutputs />', () => {
  it('should have section panels', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    decisionInstanceDetailsStore.fetchDecisionInstance('1');

    render(<InputsAndOutputs />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('inputs-skeleton'),
    );

    expect(screen.getByRole('heading', {name: /inputs/i})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: /outputs/i})).toBeInTheDocument();
  });

  it('should show a loading skeleton', async () => {
    mockFetchDecisionInstance().withServerError();

    decisionInstanceDetailsStore.fetchDecisionInstance('1');

    render(<InputsAndOutputs />, {wrapper: Wrapper});

    expect(screen.getByTestId('inputs-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('outputs-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('inputs-skeleton'),
    );

    expect(screen.queryByTestId('inputs-skeleton')).not.toBeInTheDocument();
    expect(screen.queryByTestId('outputs-skeleton')).not.toBeInTheDocument();
  });

  it('should show empty message for failed decision instances with variables', async () => {
    mockFetchDecisionInstance().withSuccess(assignApproverGroup);

    decisionInstanceDetailsStore.fetchDecisionInstance('1');

    render(<InputsAndOutputs />, {wrapper: Wrapper});

    expect(
      await screen.findByText(
        'No output available because the evaluation failed',
      ),
    ).toBeInTheDocument();

    expect(
      screen.queryByText('No input available because the evaluation failed'),
    ).not.toBeInTheDocument();
  });

  it('should show empty message for failed decision instances without variables', async () => {
    mockFetchDecisionInstance().withSuccess(
      assignApproverGroupWithoutVariables,
    );

    decisionInstanceDetailsStore.fetchDecisionInstance('1');

    render(<InputsAndOutputs />, {wrapper: Wrapper});

    expect(
      await screen.findByText(
        'No output available because the evaluation failed',
      ),
    ).toBeInTheDocument();

    expect(
      screen.getByText('No input available because the evaluation failed'),
    ).toBeInTheDocument();
  });

  it('should load inputs and outputs', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    decisionInstanceDetailsStore.fetchDecisionInstance('1');

    render(<InputsAndOutputs />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('inputs-skeleton'),
    );

    const [inputsTable, outputsTable] = screen.getAllByRole('table');

    const [inputsNameColumnHeader, inputsValueColumnHeader] = within(
      inputsTable!,
    ).getAllByRole('columnheader');
    const [
      outputsRuleColumnHeader,
      outputsNameColumnHeader,
      outputsValueColumnHeader,
    ] = within(outputsTable!).getAllByRole('columnheader');
    const [, inputsFirstTableBodyRow] = within(inputsTable!).getAllByRole(
      'row',
    );
    const [, outputsFirstTableBodyRow] = within(outputsTable!).getAllByRole(
      'row',
    );
    const [inputsNameCell, inputsValueCell] = within(
      inputsFirstTableBodyRow!,
    ).getAllByRole('cell');
    const [outputsRuleCell, outputsNameCell, outputsValueCell] = within(
      outputsFirstTableBodyRow!,
    ).getAllByRole('cell');

    expect(inputsNameColumnHeader).toBeInTheDocument();
    expect(inputsValueColumnHeader).toBeInTheDocument();
    expect(outputsRuleColumnHeader).toBeInTheDocument();
    expect(outputsNameColumnHeader).toBeInTheDocument();
    expect(outputsValueColumnHeader).toBeInTheDocument();

    expect(inputsNameCell).toBeInTheDocument();
    expect(inputsValueCell).toBeInTheDocument();
    expect(outputsRuleCell).toBeInTheDocument();
    expect(outputsNameCell).toBeInTheDocument();
    expect(outputsValueCell).toBeInTheDocument();
  });

  it('should show an error', async () => {
    mockFetchDecisionInstance().withServerError();

    decisionInstanceDetailsStore.fetchDecisionInstance('1');

    render(<InputsAndOutputs />, {wrapper: Wrapper});

    expect(
      await screen.findAllByText(/data could not be fetched/i),
    ).toHaveLength(2);
  });
});
