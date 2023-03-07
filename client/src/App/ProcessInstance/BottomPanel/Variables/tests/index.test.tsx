/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

const mockDisplayNotification = jest.fn();
jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

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
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.getByText('Name')).toBeInTheDocument();
      expect(screen.getByText('Value')).toBeInTheDocument();

      const {items} = variablesStore.state;

      items.forEach((item) => {
        const withinVariableRow = within(screen.getByTestId(item.name));

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
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
      const {items} = variablesStore.state;
      const [activeOperationVariable] = items.filter(
        ({hasActiveOperation}) => hasActiveOperation
      );

      expect(activeOperationVariable).toBeDefined();
      expect(
        within(screen.getByTestId(activeOperationVariable!.name)).getByTestId(
          'edit-variable-spinner'
        )
      ).toBeInTheDocument();

      const [inactiveOperationVariable] = items.filter(
        ({hasActiveOperation}) => !hasActiveOperation
      );

      expect(
        within(
          // eslint-disable-next-line testing-library/prefer-presence-queries
          screen.getByTestId(inactiveOperationVariable!.name!)
        ).queryByTestId('edit-variable-spinner')
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
        screen.getByTestId('skeleton-rows')
      );

      expect(
        screen.getByTitle('View full value of testVariableName')
      ).toBeInTheDocument();
    });
  });
});
