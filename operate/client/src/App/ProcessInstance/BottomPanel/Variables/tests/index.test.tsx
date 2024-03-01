/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import Variables from '../index';
import {mockVariables} from '../index.setup';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {createInstance, createVariable} from 'modules/testUtils';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {Wrapper} from './mocks';

const instanceMock = createInstance({id: '1'});

describe('Variables', () => {
  beforeEach(() => {
    flowNodeSelectionStore.init();
  });

  describe('Variables', () => {
    it('should render variables table', async () => {
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockFetchVariables().withSuccess(mockVariables);

      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

      expect(screen.getByText('Name')).toBeInTheDocument();
      expect(screen.getByText('Value')).toBeInTheDocument();

      const {items} = variablesStore.state;

      items.forEach((item) => {
        const withinVariableRow = within(
          screen.getByTestId(`variable-${item.name}`),
        );

        expect(withinVariableRow.getByText(item.name)).toBeInTheDocument();
        expect(withinVariableRow.getByText(item.value)).toBeInTheDocument();
      });
    });

    it('should show/hide spinner next to variable according to it having an active operation', async () => {
      processInstanceDetailsStore.setProcessInstance(instanceMock);
      mockFetchVariables().withSuccess(mockVariables);

      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));
      const {items} = variablesStore.state;
      const [activeOperationVariable] = items.filter(
        ({hasActiveOperation}) => hasActiveOperation,
      );

      expect(activeOperationVariable).toBeDefined();
      expect(
        within(
          screen.getByTestId(`variable-${activeOperationVariable!.name}`),
        ).getByTestId('variable-operation-spinner'),
      ).toBeInTheDocument();

      const [inactiveOperationVariable] = items.filter(
        ({hasActiveOperation}) => !hasActiveOperation,
      );

      expect(
        within(
          // eslint-disable-next-line testing-library/prefer-presence-queries
          screen.getByTestId(`variable-${inactiveOperationVariable!.name!}`),
        ).queryByTestId('variable-operation-spinner'),
      ).not.toBeInTheDocument();
    });

    it('should have a button to see full variable value', async () => {
      processInstanceDetailsStore.setProcessInstance({
        ...instanceMock,
        state: 'COMPLETED',
      });

      mockFetchVariables().withSuccess([createVariable({isPreview: true})]);

      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});

      await waitForElementToBeRemoved(() =>
        screen.getByTestId('variables-skeleton'),
      );

      expect(
        screen.getByRole('button', {
          name: 'View full value of testVariableName',
        }),
      ).toBeInTheDocument();
    });
  });
});
