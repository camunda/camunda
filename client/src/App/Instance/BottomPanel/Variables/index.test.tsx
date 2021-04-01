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
  waitFor,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {variablesStore} from 'modules/stores/variables';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import Variables from './index';
import {mockVariables} from './index.setup';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {createInstance} from 'modules/testUtils';

const EMPTY_PLACEHOLDER = 'The Flow Node has no Variables';

type Props = {
  children?: React.ReactNode;
};

const instanceMock = createInstance({id: '1'});

const Wrapper = ({children}: Props) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={[`/instances/1`]}>
        <Route path="/instances/:processInstanceId">{children} </Route>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('Variables', () => {
  beforeEach(() => {
    flowNodeSelectionStore.init();
  });
  afterEach(() => {
    currentInstanceStore.reset();
    variablesStore.reset();
    flowNodeSelectionStore.reset();
  });

  describe('Skeleton', () => {
    it('should display empty content if there are no variables', async () => {
      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json([]))
        )
      );

      render(<Variables />, {wrapper: Wrapper});
      variablesStore.fetchVariables('1');

      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
      expect(screen.getByText(EMPTY_PLACEHOLDER)).toBeInTheDocument();
    });

    it('should display skeleton on initial load', async () => {
      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});

      expect(screen.getByTestId('skeleton-rows')).toBeInTheDocument();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
    });

    it('should display spinner on second variable fetch', async () => {
      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      const variableList = variablesStore.fetchVariables('1');

      expect(screen.getByTestId('variables-spinner')).toBeInTheDocument();
      await variableList;
      expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument();
    });
  });

  describe('Variables', () => {
    it('should render variables table', async () => {
      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables('1');

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
      currentInstanceStore.setCurrentInstance(instanceMock);
      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
      const {items} = variablesStore.state;
      const [activeOperationVariable] = items.filter(
        ({hasActiveOperation}) => hasActiveOperation
      );

      expect(
        within(screen.getByTestId(activeOperationVariable.name)).getByTestId(
          'edit-variable-spinner'
        )
      ).toBeInTheDocument();

      const [inactiveOperationVariable] = items.filter(
        ({hasActiveOperation}) => !hasActiveOperation
      );

      expect(
        within(
          // @ts-expect-error ts-migrate(2345) FIXME: Type 'null' is not assignable to type 'HTMLElement... Remove this comment to see the full error message
          screen.queryByTestId(inactiveOperationVariable.name)
        ).queryByTestId('edit-variable-spinner')
      ).not.toBeInTheDocument();
    });
  });

  describe('Add variable', () => {
    it('should show/hide add variable inputs', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.queryByTestId('add-key-row')).not.toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', {name: 'Add variable'}));
      expect(screen.getByTestId('add-key-row')).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', {name: 'Exit edit mode'}));
      expect(screen.queryByTestId('add-key-row')).not.toBeInTheDocument();
    });

    it('should not allow empty value', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      fireEvent.click(screen.getByRole('button', {name: 'Add variable'}));

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      fireEvent.change(screen.getByRole('textbox', {name: /name/i}), {
        target: {value: 'test'},
      });
      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();

      fireEvent.change(screen.getByRole('textbox', {name: /value/i}), {
        target: {value: '    '},
      });

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(screen.getByTitle('Value has to be JSON')).toBeInTheDocument();
    });

    it('should not allow empty variable name', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );

      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      fireEvent.click(screen.getByRole('button', {name: 'Add variable'}));

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      fireEvent.change(screen.getByRole('textbox', {name: /value/i}), {
        target: {value: '123'},
      });
      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(screen.getByTitle('Name has to be filled')).toBeInTheDocument();

      fireEvent.change(screen.getByRole('textbox', {name: /value/i}), {
        target: {value: 'test'},
      });

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(
        screen.getByTitle('Name has to be filled and Value has to be JSON')
      ).toBeInTheDocument();

      fireEvent.change(screen.getByRole('textbox', {name: /name/i}), {
        target: {value: '   '},
      });

      expect(
        screen.getByTitle('Name is invalid and Value has to be JSON')
      ).toBeInTheDocument();

      fireEvent.change(screen.getByRole('textbox', {name: /value/i}), {
        target: {value: '"valid value"'},
      });

      expect(screen.getByTitle('Name is invalid')).toBeInTheDocument();
    });

    it('should not allow to add duplicate variables', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );

      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      fireEvent.click(screen.getByRole('button', {name: 'Add variable'}));

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      fireEvent.change(screen.getByRole('textbox', {name: /name/i}), {
        target: {value: mockVariables[0].name},
      });

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(
        screen.getByTitle('Name should be unique and Value has to be JSON')
      ).toBeInTheDocument();

      fireEvent.change(screen.getByRole('textbox', {name: /value/i}), {
        target: {value: 'invalid json'},
      });

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(
        screen.getByTitle('Name should be unique and Value has to be JSON')
      ).toBeInTheDocument();

      fireEvent.change(screen.getByRole('textbox', {name: /value/i}), {
        target: {value: '123'},
      });

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(screen.getByTitle('Name should be unique')).toBeInTheDocument();

      fireEvent.change(screen.getByRole('textbox', {name: /name/i}), {
        target: {value: 'someOtherName'},
      });
      expect(screen.getByRole('button', {name: 'Save variable'})).toBeEnabled();
    });

    it('should not allow to add variable with invalid name', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );

      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      fireEvent.click(screen.getByRole('button', {name: 'Add variable'}));

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      fireEvent.change(screen.getByRole('textbox', {name: /name/i}), {
        target: {value: '"invalid"'},
      });

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(
        screen.getByTitle('Name is invalid and Value has to be JSON')
      ).toBeInTheDocument();

      fireEvent.change(screen.getByRole('textbox', {name: /value/i}), {
        target: {value: 'invalid json'},
      });

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(
        screen.getByTitle('Name is invalid and Value has to be JSON')
      ).toBeInTheDocument();

      fireEvent.change(screen.getByRole('textbox', {name: /value/i}), {
        target: {value: '123'},
      });
      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(screen.getByTitle('Name is invalid')).toBeInTheDocument();

      fireEvent.change(screen.getByRole('textbox', {name: /name/i}), {
        target: {value: 'someOtherName'},
      });
      expect(screen.getByRole('button', {name: 'Save variable'})).toBeEnabled();
    });

    it('should save new variable', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      fireEvent.click(screen.getByRole('button', {name: 'Add variable'}));

      const newVariableName = 'newVariable';
      const newVariableValue = '1234';

      fireEvent.change(screen.getByRole('textbox', {name: /name/i}), {
        target: {value: newVariableName},
      });
      fireEvent.change(screen.getByRole('textbox', {name: /value/i}), {
        target: {value: newVariableValue},
      });

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/operation',
          (_, res, ctx) => res.once(ctx.json(null))
        )
      );

      fireEvent.click(screen.getByRole('button', {name: 'Save variable'}));

      expect(
        within(screen.getByTestId(newVariableName)).getByTestId(
          'edit-variable-spinner'
        )
      ).toBeInTheDocument();

      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) =>
            res.once(
              ctx.json([
                ...mockVariables,
                {
                  id: '2251799813686037-mwst',
                  name: 'newVariable',
                  value: '1234',
                  scopeId: '2251799813686037',
                  processInstanceId: '2251799813686037',
                  hasActiveOperation: false,
                },
              ])
            )
        )
      );

      await variablesStore.fetchVariables('with-newly-added-variable');
      expect(
        // @ts-expect-error ts-migrate(2345) FIXME: Type 'null' is not assignable to type 'HTMLElement... Remove this comment to see the full error message
        within(screen.queryByTestId(newVariableName)).queryByTestId(
          'edit-variable-spinner'
        )
      ).not.toBeInTheDocument();
    });
  });

  describe('Edit variable', () => {
    it('should show/hide edit button next to variable according to it having an active operation', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      const [activeOperationVariable] = variablesStore.state.items.filter(
        ({hasActiveOperation}) => hasActiveOperation
      );

      expect(
        within(
          // @ts-expect-error ts-migrate(2345) FIXME: Type 'null' is not assignable to type 'HTMLElement... Remove this comment to see the full error message
          screen.queryByTestId(activeOperationVariable.name)
        ).queryByTestId('edit-variable-button')
      ).not.toBeInTheDocument();

      const [inactiveOperationVariable] = variablesStore.state.items.filter(
        ({hasActiveOperation}) => !hasActiveOperation
      );

      expect(
        within(screen.getByTestId(inactiveOperationVariable.name)).getByTestId(
          'edit-variable-button'
        )
      ).toBeInTheDocument();
    });

    it('should not display edit button next to variables if instance is completed or canceled', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      const [inactiveOperationVariable] = variablesStore.state.items.filter(
        ({hasActiveOperation}) => !hasActiveOperation
      );

      expect(
        within(screen.getByTestId(inactiveOperationVariable.name)).getByTestId(
          'edit-variable-button'
        )
      ).toBeInTheDocument();

      currentInstanceStore.setCurrentInstance({
        ...instanceMock,
        state: 'CANCELED',
      });

      expect(
        within(
          // eslint-disable-next-line testing-library/prefer-presence-queries
          screen.getByTestId(inactiveOperationVariable.name)
        ).queryByTestId('edit-variable-button')
      ).not.toBeInTheDocument();
    });

    it('should show/hide edit variable inputs', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.queryByTestId('edit-value')).not.toBeInTheDocument();

      const withinFirstVariable = within(
        screen.getByTestId(variablesStore.state.items[0].name)
      );
      expect(
        withinFirstVariable.queryByTestId('edit-value')
      ).not.toBeInTheDocument();
      expect(
        withinFirstVariable.queryByRole('button', {name: 'Exit edit mode'})
      ).not.toBeInTheDocument();
      expect(
        withinFirstVariable.queryByRole('button', {name: 'Save variable'})
      ).not.toBeInTheDocument();

      fireEvent.click(withinFirstVariable.getByTestId('edit-variable-button'));

      expect(withinFirstVariable.getByTestId('edit-value')).toBeInTheDocument();
      expect(
        withinFirstVariable.getByRole('button', {name: 'Exit edit mode'})
      ).toBeInTheDocument();
      expect(
        withinFirstVariable.getByRole('button', {name: 'Save variable'})
      ).toBeInTheDocument();
    });

    it('should disable save button when nothing is changed', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.queryByTestId('edit-value')).not.toBeInTheDocument();

      const withinFirstVariable = within(
        screen.getByTestId(variablesStore.state.items[0].name)
      );

      fireEvent.click(withinFirstVariable.getByTestId('edit-variable-button'));

      expect(
        withinFirstVariable.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
    });

    it('should validate when editing variables', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.queryByTestId('edit-value')).not.toBeInTheDocument();

      const withinFirstVariable = within(
        screen.getByTestId(variablesStore.state.items[0].name)
      );

      fireEvent.click(withinFirstVariable.getByTestId('edit-variable-button'));

      const emptyValue = '';

      fireEvent.change(screen.getByTestId('edit-value'), {
        target: {value: emptyValue},
      });

      expect(
        withinFirstVariable.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(screen.getByTitle('Value has to be JSON')).toBeInTheDocument();

      fireEvent.change(screen.getByTestId('edit-value'), {
        target: {value: '   '},
      });

      expect(
        withinFirstVariable.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(screen.getByTitle('Value has to be JSON')).toBeInTheDocument();

      const invalidJSONObject = "{invalidKey: 'value'}";

      fireEvent.change(screen.getByTestId('edit-value'), {
        target: {value: invalidJSONObject},
      });

      expect(
        withinFirstVariable.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(screen.getByTitle('Value has to be JSON')).toBeInTheDocument();
    });
  });

  describe('Footer', () => {
    it('should disable add variable button when loading', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});

      expect(screen.getByText('Add Variable')).toBeDisabled();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
      expect(screen.getByText('Add Variable')).toBeEnabled();
    });

    it('should disable add variable button if instance state is cancelled', async () => {
      currentInstanceStore.setCurrentInstance({
        ...instanceMock,
        state: 'CANCELED',
      });

      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );

      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.getByText('Add Variable')).toBeDisabled();
    });

    it('should disable add variable button if add/edit variable button is clicked', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      fireEvent.click(screen.getByRole('button', {name: 'Add variable'}));
      expect(screen.getByText('Add Variable')).toBeDisabled();

      fireEvent.click(screen.getByRole('button', {name: 'Exit edit mode'}));
      expect(screen.getByText('Add Variable')).toBeEnabled();

      fireEvent.click(screen.getAllByTestId('edit-variable-button')[0]);
      expect(screen.getByText('Add Variable')).toBeDisabled();

      fireEvent.click(screen.getByRole('button', {name: 'Exit edit mode'}));
      expect(screen.getByText('Add Variable')).toBeEnabled();
    });

    it('should disable add variable button when clicked', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.getByText('Add Variable')).toBeEnabled();
      fireEvent.click(screen.getByText('Add Variable'));
      expect(screen.getByText('Add Variable')).toBeDisabled();
    });

    it('should disable add variable button when selected flow node is not running', async () => {
      mockServer.use(
        rest.get(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json([]))
        )
      );

      flowNodeMetaDataStore.init();
      currentInstanceStore.setCurrentInstance(instanceMock);
      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.getByText('Add Variable')).toBeEnabled();

      mockServer.use(
        rest.post(
          '/api/process-instances/1/flow-node-metadata',
          (_, res, ctx) =>
            res.once(
              ctx.json({
                instanceMetadata: {
                  endDate: null,
                },
              })
            )
        )
      );

      flowNodeSelectionStore.setSelection({
        flowNodeId: 'start',
        flowNodeInstanceId: '2',
        isMultiInstance: false,
      });

      await waitFor(() =>
        expect(flowNodeMetaDataStore.state.metaData).toEqual({
          instanceMetadata: {
            endDate: null,
          },
        })
      );

      expect(screen.getByText('Add Variable')).toBeEnabled();

      mockServer.use(
        rest.post(
          '/api/process-instances/1/flow-node-metadata',
          (_, res, ctx) =>
            res.once(
              ctx.json({
                instanceMetadata: {
                  endDate: '2021-03-22T12:28:00.393+0000',
                },
              })
            )
        )
      );

      flowNodeSelectionStore.setSelection({
        flowNodeId: 'neverFails',
        flowNodeInstanceId: '3',
        isMultiInstance: false,
      });

      await waitFor(() =>
        expect(flowNodeMetaDataStore.state.metaData).toEqual({
          instanceMetadata: {
            endDate: '2021-03-22T12:28:00.393+0000',
          },
        })
      );

      expect(screen.getByText('Add Variable')).toBeDisabled();

      flowNodeMetaDataStore.reset();
    });
  });
});
