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
  within,
  waitFor,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {variablesStore} from 'modules/stores/variables';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import Variables from './index';
import {mockVariables, mockMetaData} from './index.setup';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {createInstance} from 'modules/testUtils';
import {Form} from 'react-final-form';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';

const EMPTY_PLACEHOLDER = 'The Flow Node has no Variables';

type Props = {
  children?: React.ReactNode;
};

const mockDisplayNotification = jest.fn();
jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

const instanceMock = createInstance({id: '1'});

const Wrapper = ({children}: Props) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={[`/instances/1`]}>
        <Route path="/instances/:processInstanceId">
          <Form onSubmit={() => {}}>
            {({handleSubmit}) => {
              return <form onSubmit={handleSubmit}>{children} </form>;
            }}
          </Form>
          ,
        </Route>
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
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json([]))
        )
      );

      render(<Variables />, {wrapper: Wrapper});
      flowNodeMetaDataStore.setMetaData(mockMetaData);
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
      expect(await screen.findByText(EMPTY_PLACEHOLDER)).toBeInTheDocument();
    });

    it('should display skeleton on initial load', async () => {
      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});

      expect(screen.getByTestId('skeleton-rows')).toBeInTheDocument();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
    });

    it('should display spinner on second variable fetch', async () => {
      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      const variableList = variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      expect(screen.getByTestId('variables-spinner')).toBeInTheDocument();
      await variableList;
      expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument();
    });
  });

  describe('Variables', () => {
    it('should render variables table', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
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
      currentInstanceStore.setCurrentInstance(instanceMock);
      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
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
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.queryByTestId('add-key-row')).not.toBeInTheDocument();
      userEvent.click(screen.getByRole('button', {name: 'Add variable'}));
      expect(screen.getByTestId('add-key-row')).toBeInTheDocument();
      userEvent.click(screen.getByRole('button', {name: 'Exit edit mode'}));
      expect(screen.queryByTestId('add-key-row')).not.toBeInTheDocument();
    });

    it('should not allow empty value', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      userEvent.click(screen.getByRole('button', {name: 'Add variable'}));

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      userEvent.type(screen.getByRole('textbox', {name: /name/i}), 'test');
      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();

      userEvent.type(screen.getByRole('textbox', {name: /value/i}), '    ');

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(screen.getByTitle('Value has to be JSON')).toBeInTheDocument();
    });

    it('should not allow empty variable name', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );

      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      userEvent.click(screen.getByRole('button', {name: 'Add variable'}));

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      userEvent.type(screen.getByRole('textbox', {name: /value/i}), '123', {});
      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(screen.getByTitle('Name has to be filled')).toBeInTheDocument();

      userEvent.clear(screen.getByRole('textbox', {name: /value/i}));
      userEvent.type(screen.getByRole('textbox', {name: /value/i}), 'test');

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(
        screen.getByTitle('Name has to be filled and Value has to be JSON')
      ).toBeInTheDocument();

      userEvent.clear(screen.getByRole('textbox', {name: /name/i}));
      userEvent.type(screen.getByRole('textbox', {name: /name/i}), '   ');

      expect(
        screen.getByTitle('Name is invalid and Value has to be JSON')
      ).toBeInTheDocument();

      userEvent.clear(screen.getByRole('textbox', {name: /value/i}));
      userEvent.type(
        screen.getByRole('textbox', {name: /value/i}),
        '"valid value"'
      );

      expect(screen.getByTitle('Name is invalid')).toBeInTheDocument();
    });

    it('should not allow to add duplicate variables', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );

      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      userEvent.click(screen.getByRole('button', {name: 'Add variable'}));

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      userEvent.type(
        screen.getByRole('textbox', {name: /name/i}),
        mockVariables[0].name
      );

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(
        screen.getByTitle('Name should be unique and Value has to be JSON')
      ).toBeInTheDocument();

      userEvent.type(
        screen.getByRole('textbox', {name: /value/i}),
        'invalid json'
      );

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(
        screen.getByTitle('Name should be unique and Value has to be JSON')
      ).toBeInTheDocument();

      userEvent.clear(screen.getByRole('textbox', {name: /value/i}));
      userEvent.type(screen.getByRole('textbox', {name: /value/i}), '123');

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(screen.getByTitle('Name should be unique')).toBeInTheDocument();

      userEvent.clear(screen.getByRole('textbox', {name: /name/i}));
      userEvent.type(
        screen.getByRole('textbox', {name: /name/i}),
        'someOtherName'
      );
      expect(screen.getByRole('button', {name: 'Save variable'})).toBeEnabled();
    });

    it('should not allow to add variable with invalid name', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );

      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      userEvent.click(screen.getByRole('button', {name: 'Add variable'}));

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      userEvent.type(screen.getByRole('textbox', {name: /name/i}), '"invalid"');

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(
        screen.getByTitle('Name is invalid and Value has to be JSON')
      ).toBeInTheDocument();

      userEvent.type(
        screen.getByRole('textbox', {name: /value/i}),
        'invalid json'
      );

      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(
        screen.getByTitle('Name is invalid and Value has to be JSON')
      ).toBeInTheDocument();

      userEvent.clear(screen.getByRole('textbox', {name: /value/i}));
      userEvent.type(screen.getByRole('textbox', {name: /value/i}), '123');
      expect(
        screen.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(screen.getByTitle('Name is invalid')).toBeInTheDocument();

      userEvent.clear(screen.getByRole('textbox', {name: /name/i}));
      userEvent.type(
        screen.getByRole('textbox', {name: /name/i}),
        'someOtherName'
      );
      expect(screen.getByRole('button', {name: 'Save variable'})).toBeEnabled();
    });

    it('clicking edit variables while add mode is open, should not display a validation error', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      userEvent.click(screen.getByRole('button', {name: 'Add variable'}));

      const withinVariable = within(screen.getByTestId('clientNo'));
      userEvent.click(withinVariable.getByTestId('edit-variable-button'));
      expect(
        screen.queryByTitle('Name should be unique')
      ).not.toBeInTheDocument();
    });
  });

  describe('Edit variable', () => {
    it('should show/hide edit button next to variable according to it having an active operation', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

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
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

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
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

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

      userEvent.click(withinFirstVariable.getByTestId('edit-variable-button'));

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
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.queryByTestId('edit-value')).not.toBeInTheDocument();

      const withinFirstVariable = within(
        screen.getByTestId(variablesStore.state.items[0].name)
      );

      userEvent.click(withinFirstVariable.getByTestId('edit-variable-button'));

      expect(
        withinFirstVariable.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
    });

    it('should validate when editing variables', async () => {
      const originalConsoleError = global.console.error;
      global.console.error = jest.fn();

      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.queryByTestId('edit-value')).not.toBeInTheDocument();

      const withinFirstVariable = within(
        screen.getByTestId(variablesStore.state.items[0].name)
      );

      userEvent.click(withinFirstVariable.getByTestId('edit-variable-button'));

      const emptyValue = '';

      userEvent.clear(screen.getByTestId('edit-value'));
      userEvent.type(screen.getByTestId('edit-value'), emptyValue);

      expect(
        withinFirstVariable.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(screen.getByTitle('Value has to be JSON')).toBeInTheDocument();

      userEvent.clear(screen.getByTestId('edit-value'));
      userEvent.type(screen.getByTestId('edit-value'), '   ');

      expect(
        withinFirstVariable.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(screen.getByTitle('Value has to be JSON')).toBeInTheDocument();

      const invalidJSONObject = "{invalidKey: 'value'}";

      userEvent.clear(screen.getByTestId('edit-value'));
      userEvent.type(screen.getByTestId('edit-value'), invalidJSONObject);

      expect(
        withinFirstVariable.getByRole('button', {name: 'Save variable'})
      ).toBeDisabled();
      expect(screen.getByTitle('Value has to be JSON')).toBeInTheDocument();

      global.console.error = originalConsoleError;
    });

    it('should get variable details on edit button click if the variables value was a preview', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) =>
            res.once(
              ctx.json([
                {
                  id: '2251799813686037-clientNo',
                  name: 'clientNo',
                  value: '"value-preview"',
                  scopeId: '2251799813686037',
                  processInstanceId: '2251799813686037',
                  hasActiveOperation: false,
                  isPreview: true,
                },
                {
                  id: '2251799813686037-mwst',
                  name: 'mwst',
                  value: '124.26',
                  scopeId: '2251799813686037',
                  processInstanceId: '2251799813686037',
                  hasActiveOperation: false,
                },
              ])
            )
        )
      );

      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.getByText('"value-preview"')).toBeInTheDocument();
      mockServer.use(
        rest.get('/api/variables/:variableId', (_, res, ctx) =>
          res.once(
            ctx.json({
              id: '2251799813686037-clientNo',
              name: 'clientNo',
              value: '"full-value"',
              scopeId: '2251799813686037',
              processInstanceId: '2251799813686037',
              hasActiveOperation: false,
              isPreview: false,
            })
          )
        )
      );

      userEvent.click(
        within(screen.getByTestId('clientNo')).getByRole('button', {
          name: /Enter edit mode/,
        })
      );
      expect(screen.getByTestId('variable-backdrop')).toBeInTheDocument();
      expect(
        within(screen.getByTestId('mwst')).getByRole('button', {
          name: 'Enter edit mode',
        })
      ).toBeDisabled();

      await waitForElementToBeRemoved(screen.getByTestId('variable-backdrop'));

      expect(screen.queryByText('"value-preview"')).not.toBeInTheDocument();
      expect(screen.getByText('"full-value"')).toBeInTheDocument();
      expect(
        within(screen.getByTestId('mwst')).getByRole('button', {
          name: 'Enter edit mode',
        })
      ).toBeEnabled();
      expect(mockDisplayNotification).not.toHaveBeenCalled();
    });

    it('should display notification if error occurs when getting single variable details', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) =>
            res.once(
              ctx.json([
                {
                  id: '2251799813686037-clientNo',
                  name: 'clientNo',
                  value: '"value-preview"',
                  scopeId: '2251799813686037',
                  processInstanceId: '2251799813686037',
                  hasActiveOperation: false,
                  isPreview: true,
                },
              ])
            )
        )
      );

      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.getByText('"value-preview"')).toBeInTheDocument();
      mockServer.use(
        rest.get('/api/variables/:variableId', (_, res, ctx) =>
          res.once(ctx.status(500), ctx.json({}))
        )
      );

      userEvent.click(
        within(screen.getByTestId('clientNo')).getByRole('button', {
          name: /Enter edit mode/,
        })
      );
      expect(screen.getByTestId('variable-backdrop')).toBeInTheDocument();

      await waitForElementToBeRemoved(screen.getByTestId('variable-backdrop'));

      expect(screen.getByText('"value-preview"')).toBeInTheDocument();

      expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
        headline: 'Variable could not be fetched',
      });
    });

    it('should not get variable details on edit button click if the variables value was not a preview', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) =>
            res.once(
              ctx.json([
                {
                  id: '2251799813686037-clientNo',
                  name: 'clientNo',
                  value: '"full-value"',
                  scopeId: '2251799813686037',
                  processInstanceId: '2251799813686037',
                  hasActiveOperation: false,
                  isPreview: false,
                },
              ])
            )
        )
      );

      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.getByText('"full-value"')).toBeInTheDocument();

      userEvent.click(
        within(screen.getByTestId('clientNo')).getByRole('button', {
          name: /Enter edit mode/,
        })
      );

      expect(screen.queryByTestId('variable-backdrop')).not.toBeInTheDocument();
    });
  });

  describe('Footer', () => {
    it('should disable add variable button when loading', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

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
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );

      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.getByText('Add Variable')).toBeDisabled();
    });

    it('should hide/disable add variable button if add/edit variable button is clicked', async () => {
      currentInstanceStore.setCurrentInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      userEvent.click(screen.getByRole('button', {name: 'Add variable'}));
      expect(screen.queryByText('Add Variable')).not.toBeInTheDocument();

      userEvent.click(screen.getByRole('button', {name: 'Exit edit mode'}));
      expect(screen.getByText('Add Variable')).toBeEnabled();

      userEvent.click(screen.getAllByTestId('edit-variable-button')[0]);
      expect(screen.getByText('Add Variable')).toBeDisabled();

      userEvent.click(screen.getByRole('button', {name: 'Exit edit mode'}));
      expect(screen.getByText('Add Variable')).toBeEnabled();
    });

    it('should disable add variable button when selected flow node is not running', async () => {
      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables-new',
          (_, res, ctx) => res.once(ctx.json([]))
        )
      );

      flowNodeMetaDataStore.init();
      currentInstanceStore.setCurrentInstance(instanceMock);
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

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
        expect(
          flowNodeMetaDataStore.state.metaData?.instanceMetadata?.endDate
        ).toEqual(null)
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
        expect(
          flowNodeMetaDataStore.state.metaData?.instanceMetadata?.endDate
        ).toEqual(MOCK_TIMESTAMP)
      );

      expect(screen.getByText('Add Variable')).toBeDisabled();

      flowNodeMetaDataStore.reset();
    });
  });
});
