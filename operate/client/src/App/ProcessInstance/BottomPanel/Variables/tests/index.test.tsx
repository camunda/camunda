/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
