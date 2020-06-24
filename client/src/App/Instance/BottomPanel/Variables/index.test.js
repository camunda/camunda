/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {MemoryRouter, Route} from 'react-router-dom';
import {
  render,
  screen,
  fireEvent,
  within,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {variables} from 'modules/stores/variables';
import {currentInstance} from 'modules/stores/currentInstance';
import Variables from './index';
import {flowNodeInstance} from 'modules/stores/flowNodeInstance';
import {mockVariables, mockProps} from './Variables.setup';

const EMPTY_PLACEHOLDER = 'The Flow Node has no variables.';

jest.mock('modules/api/instances', () => ({
  fetchWorkflowInstance: jest.fn().mockImplementation((instanceId) => {
    if (instanceId === 'active_instance') return {id: 1, state: 'ACTIVE'};
    else if (instanceId === 'canceled_instance')
      return {id: 2, state: 'CANCELED'};
  }),
  fetchVariables: jest.fn().mockImplementation((param) => {
    if (param.instanceId === 'invalid_instance')
      return {error: 'An error occured'};
    else if (param.instanceId === 'no_variable_instance') {
      return [];
    } else if (param.instanceId === 'with-newly-added-variable') {
      return [
        ...mockVariables,
        {
          id: '2251799813686037-mwst',
          name: 'newVariable',
          value: '1234',
          scopeId: '2251799813686037',
          workflowInstanceId: '2251799813686037',
          hasActiveOperation: false,
        },
      ];
    } else {
      return mockVariables;
    }
  }),
  applyOperation: jest.fn().mockImplementation(() => {
    return null;
  }),
}));

function renderVariables(instanceId) {
  return render(
    <MemoryRouter initialEntries={[`/instances/${instanceId}`]}>
      <Route path="/instances/:id">
        <Variables {...mockProps} />
      </Route>
    </MemoryRouter>
  );
}

describe('Variables', () => {
  beforeEach(async () => {
    await currentInstance.fetchCurrentInstance('active_instance');
    variables.reset();
    variables.init();
  });

  describe('Skeleton', () => {
    it('should display empty content if there are no variables', async () => {
      renderVariables('no_variable_instance');

      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
      expect(screen.getByText(EMPTY_PLACEHOLDER)).toBeInTheDocument();
    });

    it('should display skeleton on initial load', async () => {
      renderVariables();

      expect(screen.getByTestId('skeleton-rows')).toBeInTheDocument();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
    });

    it('should display spinner on second variable fetch', async () => {
      renderVariables();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      const variableList = variables.fetchVariables(1);

      expect(screen.getByTestId('variables-spinner')).toBeInTheDocument();
      await variableList;
      expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument();
    });
  });

  describe('Variables', () => {
    it('should render variables table', async () => {
      renderVariables();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.getByText('Variable')).toBeInTheDocument();
      expect(screen.getByText('Value')).toBeInTheDocument();
      const {items} = variables.state;
      items.forEach((item) => {
        const withinVariableRow = within(screen.getByTestId(item.name));
        expect(withinVariableRow.getByText(item.name)).toBeInTheDocument();
        expect(withinVariableRow.getByText(item.value)).toBeInTheDocument();
      });
    });

    it('should show/hide spinner next to variable according to it having an active operation', async () => {
      renderVariables();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      const {items} = variables.state;

      const [activeOperationVariable] = items.filter(
        (item) => item.hasActiveOperation
      );

      expect(
        within(screen.getByTestId(activeOperationVariable.name)).getByTestId(
          'edit-variable-spinner'
        )
      ).toBeInTheDocument();

      const [inactiveOperationVariable] = items.filter(
        (item) => !item.hasActiveOperation
      );

      expect(
        within(
          screen.getByTestId(inactiveOperationVariable.name)
        ).queryByTestId('edit-variable-spinner')
      ).not.toBeInTheDocument();
    });
  });

  describe('Add variable', () => {
    it('should show/hide add variable inputs', async () => {
      renderVariables();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.queryByTestId('add-key-row')).not.toBeInTheDocument();
      fireEvent.click(screen.getByTestId('add-variable-button'));
      expect(screen.getByTestId('add-key-row')).toBeInTheDocument();
      fireEvent.click(screen.getByTestId('exit-edit-inline-btn'));
      expect(screen.queryByTestId('add-key-row')).not.toBeInTheDocument();
    });

    it('should validate when adding variable', async () => {
      renderVariables();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      fireEvent.click(screen.getByTestId('add-variable-button'));

      expect(screen.getByTestId('save-var-inline-btn')).toBeDisabled();
      fireEvent.change(screen.getByTestId('add-key'), {
        target: {value: 'test'},
      });
      expect(screen.getByTestId('save-var-inline-btn')).toBeDisabled();
      fireEvent.change(screen.getByTestId('add-value'), {
        target: {value: 'test'},
      });
      expect(screen.getByTestId('save-var-inline-btn')).toBeDisabled();
      fireEvent.change(screen.getByTestId('add-value'), {
        target: {value: '"test"'},
      });
      expect(screen.getByTestId('save-var-inline-btn')).toBeEnabled();
      fireEvent.change(screen.getByTestId('add-value'), {
        target: {value: '123'},
      });
      expect(screen.getByTestId('save-var-inline-btn')).toBeEnabled();

      fireEvent.change(screen.getByTestId('add-value'), {
        target: {value: '{}'},
      });
      expect(screen.getByTestId('save-var-inline-btn')).toBeEnabled();

      fireEvent.change(screen.getByTestId('add-key'), {
        target: {value: '"test"'},
      });
      expect(screen.getByTestId('save-var-inline-btn')).toBeDisabled();

      fireEvent.change(screen.getByTestId('add-key'), {
        target: {value: 'test'},
      });
      expect(screen.getByTestId('save-var-inline-btn')).toBeEnabled();

      const invalidJSONObject = "{invalidKey: 'value'}";

      fireEvent.change(screen.getByTestId('add-value'), {
        target: {value: invalidJSONObject},
      });
      expect(screen.getByTestId('save-var-inline-btn')).toBeDisabled();

      // already existing variable
      fireEvent.change(screen.getByTestId('add-key'), {
        target: {value: variables.state.items[0].name},
      });

      fireEvent.change(screen.getByTestId('add-value'), {
        target: {value: variables.state.items[0].value},
      });

      expect(screen.getByTestId('save-var-inline-btn')).toBeDisabled();
    });

    it('should save new variable', async () => {
      renderVariables();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      fireEvent.click(screen.getByTestId('add-variable-button'));

      const newVariableName = 'newVariable';
      const newVariableValue = '1234';

      fireEvent.change(screen.getByTestId('add-key'), {
        target: {value: newVariableName},
      });
      fireEvent.change(screen.getByTestId('add-value'), {
        target: {value: newVariableValue},
      });

      fireEvent.click(screen.getByTestId('save-var-inline-btn'));

      expect(
        within(screen.getByTestId(newVariableName)).getByTestId(
          'edit-variable-spinner'
        )
      ).toBeInTheDocument();

      await variables.fetchVariables('with-newly-added-variable');
      expect(
        within(screen.getByTestId(newVariableName)).queryByTestId(
          'edit-variable-spinner'
        )
      ).not.toBeInTheDocument();
    });
  });

  describe('Edit variable', () => {
    it('should show/hide edit button next to variable according to it having an active operation', async () => {
      renderVariables();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      const activeOperationVariable = variables.state.items.filter(
        (x) => x.hasActiveOperation
      )[0];

      expect(
        within(screen.getByTestId(activeOperationVariable.name)).queryByTestId(
          'edit-variable-button'
        )
      ).not.toBeInTheDocument();

      const inactiveOperationVariable = variables.state.items.filter(
        (x) => !x.hasActiveOperation
      )[0];

      expect(
        within(screen.getByTestId(inactiveOperationVariable.name)).getByTestId(
          'edit-variable-button'
        )
      ).toBeInTheDocument();
    });

    it('should not display edit button next to variables if instance is completed or canceled', async () => {
      renderVariables(1);
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      const activeOperationVariable = variables.state.items.filter(
        (x) => !x.hasActiveOperation
      )[0];

      expect(
        within(screen.getByTestId(activeOperationVariable.name)).getByTestId(
          'edit-variable-button'
        )
      ).toBeInTheDocument();

      await currentInstance.fetchCurrentInstance('canceled_instance');
      expect(
        within(screen.getByTestId(activeOperationVariable.name)).queryByTestId(
          'edit-variable-button'
        )
      ).not.toBeInTheDocument();
    });

    it('should show/hide edit variable inputs', async () => {
      renderVariables();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.queryByTestId('edit-value')).not.toBeInTheDocument();

      const withinFirstVariable = within(
        screen.getByTestId(variables.state.items[0].name)
      );
      expect(
        withinFirstVariable.queryByTestId('edit-value')
      ).not.toBeInTheDocument();
      expect(
        withinFirstVariable.queryByTestId('exit-edit-inline-btn')
      ).not.toBeInTheDocument();
      expect(
        withinFirstVariable.queryByTestId('save-var-inline-btn')
      ).not.toBeInTheDocument();

      fireEvent.click(withinFirstVariable.getByTestId('edit-variable-button'));

      expect(withinFirstVariable.getByTestId('edit-value')).toBeInTheDocument();
      expect(
        withinFirstVariable.getByTestId('exit-edit-inline-btn')
      ).toBeInTheDocument();
      expect(
        withinFirstVariable.getByTestId('save-var-inline-btn')
      ).toBeInTheDocument();
    });

    it('should disable save button when nothing is changed', async () => {
      renderVariables();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.queryByTestId('edit-value')).not.toBeInTheDocument();

      const withinFirstVariable = within(
        screen.getByTestId(variables.state.items[0].name)
      );

      fireEvent.click(withinFirstVariable.getByTestId('edit-variable-button'));

      expect(
        withinFirstVariable.getByTestId('save-var-inline-btn')
      ).toBeDisabled();
    });

    it('should validate when editing variables', async () => {
      renderVariables();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.queryByTestId('edit-value')).not.toBeInTheDocument();

      const withinFirstVariable = within(
        screen.getByTestId(variables.state.items[0].name)
      );

      fireEvent.click(withinFirstVariable.getByTestId('edit-variable-button'));

      const emptyValue = '';

      fireEvent.change(screen.getByTestId('edit-value'), {
        target: {value: emptyValue},
      });

      expect(
        withinFirstVariable.getByTestId('save-var-inline-btn')
      ).toBeDisabled();

      const invalidJSONObject = "{invalidKey: 'value'}";

      fireEvent.change(screen.getByTestId('edit-value'), {
        target: {value: invalidJSONObject},
      });

      expect(
        withinFirstVariable.getByTestId('save-var-inline-btn')
      ).toBeDisabled();
    });
  });

  describe('Footer', () => {
    beforeAll(async () => {
      flowNodeInstance.setCurrentSelection({flowNodeId: null, treeRowIds: []});
    });
    it('should disable add variable button when loading', async () => {
      renderVariables();

      expect(screen.getByText('Add Variable')).toBeDisabled();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
      expect(screen.getByText('Add Variable')).toBeEnabled();
    });

    it('should disable add variable button if instance state is cancelled', async () => {
      currentInstance.setCurrentInstance({
        id: 'instance_id',
        state: 'CANCELED',
      });
      renderVariables();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.getByText('Add Variable')).toBeDisabled();
    });

    it('should disable add variable button if add/edit variable button is clicked', async () => {
      renderVariables();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      fireEvent.click(screen.getByTestId('add-variable-button'));
      expect(screen.getByText('Add Variable')).toBeDisabled();

      fireEvent.click(screen.getByTestId('exit-edit-inline-btn'));
      expect(screen.getByText('Add Variable')).toBeEnabled();

      fireEvent.click(screen.getAllByTestId('edit-variable-button')[0]);
      expect(screen.getByText('Add Variable')).toBeDisabled();

      fireEvent.click(screen.getByTestId('exit-edit-inline-btn'));
      expect(screen.getByText('Add Variable')).toBeEnabled();
    });

    it('should disable add variable button when clicked', async () => {
      renderVariables();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.getByText('Add Variable')).toBeEnabled();
      fireEvent.click(screen.getByText('Add Variable'));
      expect(screen.getByText('Add Variable')).toBeDisabled();
    });
  });
});
