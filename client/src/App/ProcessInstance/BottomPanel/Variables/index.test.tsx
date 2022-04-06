/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {MemoryRouter, Route, Routes} from 'react-router-dom';
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
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import Variables from './index';
import {mockVariables, mockMetaData} from './index.setup';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {createInstance} from 'modules/testUtils';
import {Form} from 'react-final-form';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {authenticationStore} from 'modules/stores/authentication';

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

const Wrapper: React.FC<Props> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={[`/processes/1`]}>
        <Routes>
          <Route
            path="/processes/:processInstanceId"
            element={
              <Form onSubmit={() => {}}>
                {({handleSubmit}) => {
                  return <form onSubmit={handleSubmit}>{children} </form>;
                }}
              </Form>
            }
          />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('Variables', () => {
  beforeEach(() => {
    flowNodeSelectionStore.init();
  });
  afterEach(() => {
    processInstanceDetailsStore.reset();
    variablesStore.reset();
    flowNodeSelectionStore.reset();
  });

  describe('Skeleton', () => {
    it('should display empty content if there are no variables', async () => {
      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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
          '/api/process-instances/:instanceId/variables',
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
          '/api/process-instances/:instanceId/variables',
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
          '/api/process-instances/:instanceId/variables',
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
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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
      processInstanceDetailsStore.setProcessInstance(instanceMock);
      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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
          // @ts-expect-error ts-migrate(2345) FIXME: Type 'null' is not assignable to type 'HTMLElement... Remove this comment to see the full error message
          screen.queryByTestId(inactiveOperationVariable.name)
        ).queryByTestId('edit-variable-spinner')
      ).not.toBeInTheDocument();
    });
  });

  describe('Add variable', () => {
    it('should show/hide add variable inputs', async () => {
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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
      userEvent.click(screen.getByTitle(/add variable/i));
      expect(screen.getByTestId('add-key-row')).toBeInTheDocument();
      userEvent.click(screen.getByTitle(/exit edit mode/i));
      expect(screen.queryByTestId('add-key-row')).not.toBeInTheDocument();
    });

    it('should not allow empty value', async () => {
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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

      userEvent.click(screen.getByTitle(/add variable/i));

      expect(screen.getByTitle(/save variable/i)).toBeDisabled();

      userEvent.type(screen.getByTestId('add-variable-name'), 'test');
      expect(screen.getByTitle(/save variable/i)).toBeDisabled();

      userEvent.type(screen.getByTestId('add-variable-value'), '    ');

      expect(screen.getByTitle(/save variable/i)).toBeDisabled();
      expect(screen.queryByTitle('Invalid input text')).not.toBeInTheDocument();
      expect(await screen.findByText('Invalid input text')).toBeInTheDocument();
    });

    it('should not allow empty characters in variable name', async () => {
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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

      userEvent.click(screen.getByTitle(/add variable/i));

      expect(screen.getByTitle(/save variable/i)).toBeDisabled();
      userEvent.type(screen.getByTestId('add-variable-value'), '123', {});
      expect(screen.getByTitle(/save variable/i)).toBeDisabled();
      expect(
        screen.queryByText('Name has to be filled')
      ).not.toBeInTheDocument();
      expect(
        await screen.findByText('Name has to be filled')
      ).toBeInTheDocument();

      userEvent.clear(screen.getByTestId('add-variable-value'));
      userEvent.type(screen.getByTestId('add-variable-value'), 'test');
      expect(screen.getByTitle(/save variable/i)).toBeDisabled();

      expect(screen.getByText('Name has to be filled')).toBeInTheDocument();
      expect(screen.queryByText('Invalid input text')).not.toBeInTheDocument();
      expect(await screen.findByText('Invalid input text')).toBeInTheDocument();

      userEvent.type(screen.getByTestId('add-variable-name'), '   ');

      expect(screen.getByText('Invalid input text')).toBeInTheDocument();
      expect(await screen.findByText('Name is invalid')).toBeInTheDocument();

      userEvent.type(screen.getByTestId('add-variable-name'), ' test');

      expect(screen.getByText('Invalid input text')).toBeInTheDocument();
      expect(screen.getByText('Name is invalid')).toBeInTheDocument();

      userEvent.clear(screen.getByTestId('add-variable-value'));
      userEvent.type(screen.getByTestId('add-variable-value'), '"valid value"');

      await waitForElementToBeRemoved(() =>
        screen.getByText('Invalid input text')
      );

      expect(screen.getByText('Name is invalid')).toBeInTheDocument();
    });

    it('should not allow to add duplicate variables', async () => {
      jest.useFakeTimers();
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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

      userEvent.click(screen.getByTitle(/add variable/i));

      expect(screen.getByTitle(/save variable/i)).toBeDisabled();
      userEvent.type(
        screen.getByTestId('add-variable-name'),
        mockVariables[0].name
      );

      expect(screen.getByTitle(/save variable/i)).toBeDisabled();
      expect(
        screen.queryByText('Name should be unique')
      ).not.toBeInTheDocument();
      expect(screen.queryByText('Invalid input text')).not.toBeInTheDocument();
      expect(
        await screen.findByText('Name should be unique')
      ).toBeInTheDocument();
      expect(await screen.findByText('Invalid input text')).toBeInTheDocument();

      userEvent.clear(screen.getByTestId('add-variable-value'));
      userEvent.type(screen.getByTestId('add-variable-value'), '123');

      expect(screen.getByTitle(/save variable/i)).toBeDisabled();

      await waitForElementToBeRemoved(() =>
        screen.getByText('Invalid input text')
      );
      expect(screen.getByText('Name should be unique')).toBeInTheDocument();

      userEvent.clear(screen.getByTestId('add-variable-name'));
      userEvent.type(screen.getByTestId('add-variable-name'), 'someOtherName');

      await waitFor(() =>
        expect(screen.getByTitle(/save variable/i)).toBeEnabled()
      );

      expect(
        screen.queryByText('Name should be unique')
      ).not.toBeInTheDocument();

      jest.clearAllTimers();
      jest.useRealTimers();
    });

    it('should not allow to add variable with invalid name', async () => {
      jest.useFakeTimers();
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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

      userEvent.click(screen.getByTitle(/add variable/i));

      expect(screen.getByTitle(/save variable/i)).toBeDisabled();
      userEvent.type(screen.getByTestId('add-variable-name'), '"invalid"');
      userEvent.type(screen.getByTestId('add-variable-value'), '123');

      expect(screen.getByTitle(/save variable/i)).toBeDisabled();

      expect(screen.queryByText('Name is invalid')).not.toBeInTheDocument();

      jest.runOnlyPendingTimers();

      expect(await screen.findByText('Name is invalid')).toBeInTheDocument();

      userEvent.clear(screen.getByTestId('add-variable-name'));
      userEvent.type(screen.getByTestId('add-variable-name'), 'someOtherName');

      jest.runOnlyPendingTimers();

      await waitFor(() =>
        expect(screen.getByTitle(/save variable/i)).toBeEnabled()
      );

      expect(screen.queryByText('Name is invalid')).not.toBeInTheDocument();

      jest.clearAllTimers();
      jest.useRealTimers();
    });

    it('clicking edit variables while add mode is open, should not display a validation error', async () => {
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables('1');

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      userEvent.click(screen.getByTitle(/add variable/i));

      const withinVariable = within(screen.getByTestId('clientNo'));
      userEvent.click(withinVariable.getByTestId('edit-variable-button'));
      expect(
        screen.queryByTitle('Name should be unique')
      ).not.toBeInTheDocument();
    });
  });

  describe('Edit variable', () => {
    it('should show/hide edit button next to variable according to it having an active operation', async () => {
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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

      expect(inactiveOperationVariable).toBeDefined();
      expect(
        within(screen.getByTestId(inactiveOperationVariable!.name)).getByTestId(
          'edit-variable-button'
        )
      ).toBeInTheDocument();
    });

    it('should not display edit button next to variables if instance is completed or canceled', async () => {
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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

      expect(inactiveOperationVariable).toBeDefined();
      expect(
        within(screen.getByTestId(inactiveOperationVariable!.name)).getByTestId(
          'edit-variable-button'
        )
      ).toBeInTheDocument();

      processInstanceDetailsStore.setProcessInstance({
        ...instanceMock,
        state: 'CANCELED',
      });

      expect(
        within(
          // eslint-disable-next-line testing-library/prefer-presence-queries
          screen.getByTestId(inactiveOperationVariable!.name)
        ).queryByTestId('edit-variable-button')
      ).not.toBeInTheDocument();
    });

    it('should show/hide edit variable inputs', async () => {
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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

      expect(
        screen.queryByTestId('add-variable-value')
      ).not.toBeInTheDocument();

      const [firstVariable] = variablesStore.state.items;
      expect(firstVariable).toBeDefined();
      const withinFirstVariable = within(
        screen.getByTestId(firstVariable!.name)
      );
      expect(
        withinFirstVariable.queryByTestId('edit-variable-value')
      ).not.toBeInTheDocument();
      expect(
        withinFirstVariable.queryByTitle(/exit edit mode/i)
      ).not.toBeInTheDocument();
      expect(
        withinFirstVariable.queryByTitle(/save variable/i)
      ).not.toBeInTheDocument();

      userEvent.click(withinFirstVariable.getByTestId('edit-variable-button'));

      expect(
        withinFirstVariable.getByTestId('edit-variable-value')
      ).toBeInTheDocument();
      expect(
        withinFirstVariable.getByTitle(/exit edit mode/i)
      ).toBeInTheDocument();
      expect(
        withinFirstVariable.getByTitle(/save variable/i)
      ).toBeInTheDocument();
    });

    it('should disable save button when nothing is changed', async () => {
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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

      expect(
        screen.queryByTestId('add-variable-value')
      ).not.toBeInTheDocument();

      const [firstVariable] = variablesStore.state.items;
      expect(firstVariable).toBeDefined();
      const withinFirstVariable = within(
        screen.getByTestId(firstVariable!.name)
      );

      userEvent.click(withinFirstVariable.getByTestId('edit-variable-button'));

      expect(withinFirstVariable.getByTitle(/save variable/i)).toBeDisabled();
    });

    it('should validate when editing variables', async () => {
      jest.useFakeTimers();

      const originalConsoleError = global.console.error;
      global.console.error = jest.fn();

      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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

      expect(
        screen.queryByTestId('edit-variable-value')
      ).not.toBeInTheDocument();

      const [firstVariable] = variablesStore.state.items;
      expect(firstVariable).toBeDefined();
      const withinFirstVariable = within(
        screen.getByTestId(firstVariable!.name)
      );

      userEvent.click(withinFirstVariable.getByTestId('edit-variable-button'));

      const invalidJSONObject = "{invalidKey: 'value'}";

      userEvent.clear(screen.getByTestId('edit-variable-value'));
      userEvent.type(
        screen.getByTestId('edit-variable-value'),
        invalidJSONObject
      );

      expect(withinFirstVariable.getByTitle(/save variable/i)).toBeDisabled();

      expect(screen.queryByText('Invalid input text')).not.toBeInTheDocument();

      jest.runOnlyPendingTimers();
      expect(await screen.findByText('Invalid input text')).toBeInTheDocument();

      userEvent.clear(screen.getByTestId('edit-variable-value'));
      userEvent.type(screen.getByTestId('edit-variable-value'), '123');

      await waitForElementToBeRemoved(() =>
        screen.getByText('Invalid input text')
      );
      expect(screen.getByTitle(/save variable/i)).toBeEnabled();

      global.console.error = originalConsoleError;
      jest.clearAllTimers();
      jest.useRealTimers();
    });

    it('should get variable details on edit button click if the variables value was a preview', async () => {
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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
        within(screen.getByTestId('clientNo')).getByTitle(/enter edit mode/i)
      );
      expect(screen.getByTestId('variable-backdrop')).toBeInTheDocument();
      expect(
        within(screen.getByTestId('mwst')).getByTitle(/enter edit mode/i)
      ).toBeDisabled();

      await waitForElementToBeRemoved(screen.getByTestId('variable-backdrop'));

      expect(screen.queryByText('"value-preview"')).not.toBeInTheDocument();

      expect(screen.getByTestId('edit-variable-value')).toHaveValue(
        '"full-value"'
      );
      expect(
        within(screen.getByTestId('mwst')).getByTitle(/enter edit mode/i)
      ).toBeEnabled();
      expect(mockDisplayNotification).not.toHaveBeenCalled();
    });

    it('should display notification if error occurs when getting single variable details', async () => {
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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
        within(screen.getByTestId('clientNo')).getByTitle(/enter edit mode/i)
      );
      expect(screen.getByTestId('variable-backdrop')).toBeInTheDocument();

      await waitForElementToBeRemoved(screen.getByTestId('variable-backdrop'));

      expect(screen.getByText('"value-preview"')).toBeInTheDocument();

      expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
        headline: 'Variable could not be fetched',
      });
    });

    it('should not get variable details on edit button click if the variables value was not a preview', async () => {
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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
        within(screen.getByTestId('clientNo')).getByTitle(/enter edit mode/i)
      );

      expect(screen.queryByTestId('variable-backdrop')).not.toBeInTheDocument();
    });
  });

  describe('Footer', () => {
    it('should disable add variable button when loading', async () => {
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json(mockVariables))
        )
      );
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});

      expect(screen.getByText(/add variable/i)).toBeDisabled();
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
      expect(screen.getByText(/add variable/i)).toBeEnabled();
    });

    it('should disable add variable button if instance state is cancelled', async () => {
      processInstanceDetailsStore.setProcessInstance({
        ...instanceMock,
        state: 'CANCELED',
      });

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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

      expect(screen.getByText(/add variable/i)).toBeDisabled();
    });

    it('should hide/disable add variable button if add/edit variable button is clicked', async () => {
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
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

      userEvent.click(screen.getByTitle(/add variable/i));
      expect(screen.queryByText(/add variable/i)).not.toBeInTheDocument();

      userEvent.click(screen.getByTitle(/exit edit mode/i));
      expect(screen.getByText(/add variable/i)).toBeEnabled();

      const [firstEditVariableButton] = screen.getAllByTestId(
        'edit-variable-button'
      );
      expect(firstEditVariableButton).toBeInTheDocument();
      userEvent.click(firstEditVariableButton!);
      expect(screen.getByText(/add variable/i)).toBeDisabled();

      userEvent.click(screen.getByTitle(/exit edit mode/i));
      expect(screen.getByText(/add variable/i)).toBeEnabled();
    });

    it('should disable add variable button when selected flow node is not running', async () => {
      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json([]))
        )
      );

      flowNodeMetaDataStore.init();
      processInstanceDetailsStore.setProcessInstance(instanceMock);
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.getByText(/add variable/i)).toBeEnabled();

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

      expect(screen.getByText(/add variable/i)).toBeEnabled();

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

      expect(screen.getByText(/add variable/i)).toBeDisabled();

      flowNodeMetaDataStore.reset();
    });
  });

  it('should have JSON editor when adding a new Variable', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.json(mockVariables))
      )
    );
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    userEvent.click(screen.getByTitle(/add variable/i));
    userEvent.click(screen.getByTitle(/open json editor modal/i));

    expect(
      within(screen.getByTestId('modal')).getByTitle(/close/i)
    ).toBeEnabled();
    expect(
      within(screen.getByTestId('modal')).getByTitle(/save/i)
    ).toBeEnabled();
    expect(
      within(screen.getByTestId('modal')).getByTestId('json-editor-container')
    ).toBeInTheDocument();
  });

  it('should have JSON editor when editing a Variable', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.json([mockVariables[0]]))
      )
    );
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    userEvent.click(screen.getByTitle(/enter edit mode/i));
    userEvent.click(screen.getByTitle(/open json editor modal/i));

    expect(
      within(screen.getByTestId('modal')).getByTitle(/close/i)
    ).toBeEnabled();
    expect(
      within(screen.getByTestId('modal')).getByTitle(/save/i)
    ).toBeEnabled();
    expect(
      within(screen.getByTestId('modal')).getByTestId('json-editor-container')
    ).toBeInTheDocument();
  });

  describe('Restricted user', () => {
    beforeAll(() => {
      authenticationStore.setUser({
        displayName: 'demo',
        permissions: ['read'],
        canLogout: true,
        userId: 'demo',
      });
    });

    afterAll(() => {
      authenticationStore.reset();
    });

    it('should not display Edit Variable button', async () => {
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json([mockVariables[0]]))
        )
      );
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.queryByTitle(/enter edit mode/i)).not.toBeInTheDocument();
    });

    it('should not display Add Variable footer', async () => {
      processInstanceDetailsStore.setProcessInstance(instanceMock);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/variables',
          (_, res, ctx) => res.once(ctx.json([mockVariables[0]]))
        )
      );
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });

      render(<Variables />, {wrapper: Wrapper});
      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

      expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();
    });
  });
});
