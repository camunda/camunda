/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {createMemoryHistory} from 'history';
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';

import {
  DEFAULT_FILTER,
  DEFAULT_FILTER_CONTROLLED_VALUES,
} from 'modules/constants';
import {
  flushPromises,
  mockWorkflowStatistics,
  mockWorkflowInstances,
} from 'modules/testUtils';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import Filters from './index';
import {FiltersPanel} from './FiltersPanel';
import {
  groupedWorkflowsMock,
  mockProps,
  mockPropsWithEmptyLocationSearch,
  mockPropsWithInitFilter,
  mockPropsWithDefaultFilter,
  COMPLETE_FILTER,
} from './index.setup';

import {DEBOUNCE_DELAY} from './constants';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {filtersStore} from 'modules/stores/filters';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';

jest.mock('./constants');
jest.mock('modules/utils/bpmn');

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  return (
    <CollapsablePanelProvider>
      <ThemeProvider>{children}</ThemeProvider>
    </CollapsablePanelProvider>
  );
};

describe('Filters', () => {
  const locationMock = {pathname: '/instances'};
  const historyMock = createMemoryHistory();
  beforeEach(async () => {
    mockServer.use(
      rest.post('/api/workflow-instances', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowInstances))
      ),
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      ),
      rest.get('/api/workflows/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedWorkflowsMock))
      ),
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowStatistics))
      )
    );

    await instancesDiagramStore.fetchWorkflowXml('1');
    filtersStore.setUrlParameters(historyMock, locationMock);
    await filtersStore.init();
  });

  afterEach(() => {
    filtersStore.reset();
    jest.clearAllMocks();
    jest.clearAllTimers();
  });

  it('should render filters panel if no filter is applied in querystring', () => {
    // given

    filtersStore.setFilter(DEFAULT_FILTER_CONTROLLED_VALUES);
    const node = shallow(
      <Filters.WrappedComponent
        {...mockPropsWithEmptyLocationSearch}
        // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
        filter={DEFAULT_FILTER_CONTROLLED_VALUES}
      />
    );

    // then
    expect(node.find(FiltersPanel).exists()).toBe(true);
  });

  it('should render with the right initial state', () => {
    // given
    const node = shallow(
      <Filters.WrappedComponent
        {...mockProps}
        // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
        filter={DEFAULT_FILTER_CONTROLLED_VALUES}
      />
    );

    // then
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.activityId).toEqual('');
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.workflow).toEqual('');
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.version).toEqual('');
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.startDate).toEqual('');
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.endDate).toEqual('');
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.ids).toEqual('');
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.errorMessage).toEqual('');
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.batchOperationId).toEqual('');
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.variable).toEqual({name: '', value: ''});
  });

  it('should render with prefilled input fields', () => {
    // given
    const node = shallow(
      <Filters.WrappedComponent
        {...mockPropsWithInitFilter}
        // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
        filter={COMPLETE_FILTER}
      />
    );

    // then
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.activityId).toEqual(COMPLETE_FILTER.activityId);
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.workflow).toEqual(COMPLETE_FILTER.workflow);
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.version).toEqual(COMPLETE_FILTER.version);
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.startDate).toEqual(COMPLETE_FILTER.startDate);
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.endDate).toEqual(COMPLETE_FILTER.endDate);
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.ids).toEqual(COMPLETE_FILTER.ids);
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.errorMessage).toEqual(
      COMPLETE_FILTER.errorMessage
    );
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.variable).toEqual(COMPLETE_FILTER.variable);
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
    expect(node.state().filter.batchOperationId).toEqual(
      COMPLETE_FILTER.batchOperationId
    );
  });

  it('should render the running and finished filters', () => {
    render(
      <Filters.WrappedComponent
        {...mockProps}
        // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
        filter={DEFAULT_FILTER_CONTROLLED_VALUES}
      />,
      {wrapper: Wrapper}
    );

    expect(
      screen.getByRole('checkbox', {name: 'Running Instances'})
    ).toBeInTheDocument();
    expect(screen.getByRole('checkbox', {name: 'Active'})).toBeInTheDocument();
    expect(
      screen.getByRole('checkbox', {name: 'Incidents'})
    ).toBeInTheDocument();

    expect(
      screen.getByRole('checkbox', {name: 'Finished Instances'})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('checkbox', {name: 'Completed'})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('checkbox', {name: 'Canceled'})
    ).toBeInTheDocument();
  });

  describe('errorMessage filter', () => {
    it('should render an errorMessage field', async () => {
      render(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />,
        {wrapper: Wrapper}
      );

      const errorMessageField = screen.getByRole('textbox', {
        name: 'Error Message',
      }) as HTMLInputElement;

      expect(errorMessageField.placeholder).toBe('Error Message');
      expect(errorMessageField.value).toBe('');

      fireEvent.change(errorMessageField, {target: {value: 'asd'}});
      expect(errorMessageField.value).toBe('asd');

      //@ts-expect-error
      expect(filtersStore.state.filter.errorMessage).toBe(undefined);
      await waitFor(() =>
        //@ts-expect-error
        expect(filtersStore.state.filter.errorMessage).toBe('asd')
      );
    });

    it('should be prefilled with the value from props.filter.errorMessage ', async () => {
      render(
        <Filters.WrappedComponent
          {...mockPropsWithInitFilter}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={COMPLETE_FILTER}
        />,
        {wrapper: Wrapper}
      );

      const errorMessageField = screen.getByRole('textbox', {
        name: 'Error Message',
      }) as HTMLInputElement;

      expect(errorMessageField.value).toBe('This is an error message');
    });

    it('should set errorMessage filter value with the right error message', (done) => {
      // given
      const errorMessage = 'lorem ipsum';
      const node = shallow(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );
      const instance = node.instance();

      //when
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'handleControlledInputChange' does not ex... Remove this comment to see the full error message
      instance.handleControlledInputChange({
        target: {value: errorMessage, name: 'errorMessage'},
      });
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'waitForTimer' does not exist on type 'Co... Remove this comment to see the full error message
      instance.waitForTimer(instance.propagateFilter);

      setTimeout(() => {
        // then
        // @ts-expect-error ts-migrate(2339) FIXME: Property 'errorMessage' does not exist on type '{}... Remove this comment to see the full error message
        expect(filtersStore.state.filter.errorMessage).toBe(errorMessage);
        done();
      }, DEBOUNCE_DELAY * 2);
    });

    it('should set errorMessage filter undefined when it is changed with empty value', (done) => {
      // given
      const emptyErrorMessage = '';
      const node = shallow(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );
      const instance = node.instance();
      //when
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'handleControlledInputChange' does not ex... Remove this comment to see the full error message
      instance.handleControlledInputChange({
        target: {value: emptyErrorMessage, name: 'errorMessage'},
      });
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'waitForTimer' does not exist on type 'Co... Remove this comment to see the full error message
      instance.waitForTimer(instance.propagateFilter);

      setTimeout(() => {
        // @ts-expect-error ts-migrate(2339) FIXME: Property 'errorMessage' does not exist on type '{}... Remove this comment to see the full error message
        expect(filtersStore.state.filter.errorMessage).toBe(undefined);
        done();
      }, DEBOUNCE_DELAY * 2);
    });
  });

  describe('ids filter', () => {
    it('should render an ids field', async () => {
      render(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />,
        {wrapper: Wrapper}
      );
      const idsField = screen.getByRole('textbox', {
        name: 'Instance Id(s) separated by space or comma',
      }) as HTMLInputElement;

      expect(idsField.placeholder).toBe(
        'Instance Id(s) separated by space or comma'
      );
      expect(idsField.value).toBe('');

      fireEvent.change(idsField, {target: {value: '0000000000000001'}});
      expect(idsField.value).toBe('0000000000000001');

      await waitFor(() =>
        //@ts-expect-error
        expect(filtersStore.state.filter.ids).toBe('0000000000000001')
      );
    });

    it('should be prefilled with the value from props.filter.ids ', async () => {
      render(
        <Filters.WrappedComponent
          {...mockPropsWithInitFilter}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={{
            ...DEFAULT_FILTER_CONTROLLED_VALUES,
            ids: '0000000000000001, 0000000000000002',
          }}
        />,
        {wrapper: Wrapper}
      );

      const idsField = screen.getByRole('textbox', {
        name: 'Instance Id(s) separated by space or comma',
      }) as HTMLInputElement;

      expect(idsField.value).toBe('0000000000000001, 0000000000000002');
    });

    it('should set filter state with the right instance ids', () => {
      const instanceIds = '0000000000000001, 0000000000000002';
      // given
      const node = shallow(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      const instance = node.instance();
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'handleControlledInputChange' does not ex... Remove this comment to see the full error message
      instance.handleControlledInputChange({
        target: {value: instanceIds, name: 'ids'},
      });
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'propagateFilter' does not exist on type ... Remove this comment to see the full error message
      instance.propagateFilter();

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'ids' does not exist on type '{}'.
      expect(filtersStore.state.filter.ids).toBe(instanceIds);
    });

    it('should set filter state with empty object', () => {
      // given
      // user blurs without writing
      const emptyInstanceIds = '';
      const node = shallow(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      const instance = node.instance();
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'handleControlledInputChange' does not ex... Remove this comment to see the full error message
      instance.handleControlledInputChange({
        target: {value: emptyInstanceIds, name: 'ids'},
      });
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'propagateFilter' does not exist on type ... Remove this comment to see the full error message
      instance.propagateFilter();

      // then
      expect(filtersStore.state.filter).toEqual({});
    });
  });

  describe('workflow filter', () => {
    it('should render a workflow select field', () => {
      render(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />,
        {wrapper: Wrapper}
      );

      expect(
        screen.getByRole('combobox', {name: 'Workflow'})
      ).toBeInTheDocument();
      expect(
        screen.getByRole('option', {name: 'Big variable process'})
      ).toBeInTheDocument();
      expect(
        screen.getByRole('option', {name: 'eventBasedGatewayProcess'})
      ).toBeInTheDocument();
      expect(
        screen.getByRole('option', {name: 'New demo process'})
      ).toBeInTheDocument();
      expect(screen.getByRole('option', {name: 'Order'})).toBeInTheDocument();

      fireEvent.change(screen.getByRole('combobox', {name: 'Workflow'}), {
        target: {value: 'bigVarProcess'},
      });

      const workflow = screen.getByRole('combobox', {
        name: 'Workflow',
      }) as HTMLInputElement;

      expect(workflow.value).toBe('bigVarProcess');
      expect(filtersStore.state.filter).toEqual({
        version: '1',
        workflow: 'bigVarProcess',
      });
    });

    it('should be prefilled with the value from this.props.filter.workflow', () => {
      render(
        <Filters.WrappedComponent
          {...mockPropsWithInitFilter}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={COMPLETE_FILTER}
        />,
        {wrapper: Wrapper}
      );

      const workflow = screen.getByRole('combobox', {
        name: 'Workflow',
      }) as HTMLInputElement;

      expect(workflow.value).toBe('demoProcess');
    });
  });

  describe('version filter', () => {
    it('should render', () => {
      render(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />,
        {wrapper: Wrapper}
      );

      expect(
        screen.getByRole('combobox', {name: 'Workflow Version'})
      ).toHaveAttribute('disabled');

      fireEvent.change(screen.getByRole('combobox', {name: 'Workflow'}), {
        target: {value: 'demoProcess'},
      });

      expect(
        screen.getByRole('combobox', {name: 'Workflow Version'})
      ).not.toHaveAttribute('disabled');

      const version = screen.getByRole('combobox', {
        name: 'Workflow Version',
      }) as HTMLInputElement;

      expect(
        within(version).getByRole('option', {name: 'Version 3'})
      ).toBeInTheDocument();
      expect(
        within(version).getByRole('option', {name: 'Version 2'})
      ).toBeInTheDocument();
      expect(
        within(version).getByRole('option', {name: 'Version 1'})
      ).toBeInTheDocument();
      expect(
        within(version).getByRole('option', {name: 'All versions'})
      ).toBeInTheDocument();

      expect(version.value).toBe('3');
      expect(filtersStore.state.filter).toEqual({
        version: '3',
        workflow: 'demoProcess',
      });
    });

    it('should be prefilled with the value from this.props.filter.version', () => {
      render(
        <Filters.WrappedComponent
          {...mockPropsWithInitFilter}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={COMPLETE_FILTER}
        />,
        {wrapper: Wrapper}
      );

      const version = screen.getByRole('combobox', {
        name: 'Workflow Version',
      }) as HTMLInputElement;

      expect(version.value).toBe('2');
    });

    it('should set filter state according to version and do not allow the selection of an invalid option', async () => {
      render(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />,
        {wrapper: Wrapper}
      );

      fireEvent.change(screen.getByRole('combobox', {name: 'Workflow'}), {
        target: {value: 'demoProcess'},
      });

      fireEvent.change(
        screen.getByRole('combobox', {name: 'Workflow Version'}),
        {
          target: {value: '1'},
        }
      );

      // @ts-expect-error
      expect(filtersStore.state.filter.version).toBe('1');
      fireEvent.change(
        screen.getByRole('combobox', {name: 'Workflow Version'}),
        {
          target: {value: ''},
        }
      );

      // @ts-expect-error
      expect(filtersStore.state.filter.version).toBe('1');
      fireEvent.change(
        screen.getByRole('combobox', {name: 'Workflow Version'}),
        {
          target: {value: '3'},
        }
      );

      // @ts-expect-error
      expect(filtersStore.state.filter.version).toBe('3');

      fireEvent.change(
        screen.getByRole('combobox', {name: 'Workflow Version'}),
        {
          target: {value: 'all'},
        }
      );

      // @ts-expect-error
      expect(filtersStore.state.filter.version).toBe('all');
    });

    it('should reset after a the workflowName field is also reseted ', async () => {
      render(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />,
        {wrapper: Wrapper}
      );

      fireEvent.change(screen.getByRole('combobox', {name: 'Workflow'}), {
        target: {value: 'demoProcess'},
      });
      const version = screen.getByRole('combobox', {
        name: 'Workflow Version',
      }) as HTMLInputElement;

      expect(version.value).toBe('3');
      expect(filtersStore.state.filter).toEqual({
        version: '3',
        workflow: 'demoProcess',
      });

      fireEvent.change(screen.getByRole('combobox', {name: 'Workflow'}), {
        target: {value: ''},
      });

      expect(version.value).toBe('');
      expect(filtersStore.state.filter).toEqual({});
    });
  });

  describe('selectable FlowNode filter', () => {
    it('should render and be disabled by default', () => {
      render(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />,
        {wrapper: Wrapper}
      );

      const flowNode = screen.getByRole('combobox', {
        name: 'Flow Node',
      }) as HTMLInputElement;
      expect(flowNode).toHaveAttribute('disabled');

      expect(within(flowNode).getByRole('option', {name: 'Flow Node'}));
      expect(within(flowNode).getByRole('option', {name: 'End Event'}));
      expect(within(flowNode).getByRole('option', {name: 'Exclusive Gateway'}));
      expect(
        within(flowNode).getByRole('option', {name: 'Message Catch Event'})
      );
      expect(within(flowNode).getByRole('option', {name: 'Parallel Gateway'}));
      expect(within(flowNode).getByRole('option', {name: 'task D'}));
      expect(within(flowNode).getByRole('option', {name: 'Timer Catch Event'}));
    });

    it.skip('should be prefilled with the value from this.props.filter.activityId', async () => {
      render(
        <Filters.WrappedComponent
          {...mockPropsWithInitFilter}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={COMPLETE_FILTER}
        />,
        {wrapper: Wrapper}
      );

      const flowNode = screen.getByRole('combobox', {
        name: 'Flow Node',
      }) as HTMLInputElement;

      await waitFor(() => expect(flowNode.value).toEqual('4'), {timeout: 5000});
    });

    it('should be disabled/enabled according to selected version', async () => {
      render(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />,
        {wrapper: Wrapper}
      );

      fireEvent.change(screen.getByRole('combobox', {name: 'Workflow'}), {
        target: {value: 'demoProcess'},
      });

      fireEvent.change(
        screen.getByRole('combobox', {name: 'Workflow Version'}),
        {
          target: {value: '3'},
        }
      );

      const flowNode = screen.getByRole('combobox', {
        name: 'Flow Node',
      }) as HTMLInputElement;

      expect(flowNode).not.toHaveAttribute('disabled');

      fireEvent.change(
        screen.getByRole('combobox', {name: 'Workflow Version'}),
        {
          target: {value: 'all'},
        }
      );

      expect(flowNode).toHaveAttribute('disabled');
    });

    it('should render the selectable flow nodes on the correct order', async () => {
      filtersStore.setFilter({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        workflow: 'demoProcess',
        version: '2',
      });

      render(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={{
            ...DEFAULT_FILTER_CONTROLLED_VALUES,
            workflow: 'demoProcess',
            version: '2',
          }}
        />,
        {wrapper: Wrapper}
      );

      const flowNode = screen.getByRole('combobox', {
        name: 'Flow Node',
      }) as HTMLInputElement;

      const options = within(flowNode).getAllByRole(
        'option'
      ) as HTMLInputElement[];

      expect(options[1].textContent).toBe('End Event');
      expect(options[2].textContent).toBe('Exclusive Gateway');
      expect(options[3].textContent).toBe('Message Catch Event');
      expect(options[4].textContent).toBe('Parallel Gateway');
      expect(options[5].textContent).toBe('task D');
      expect(options[6].textContent).toBe('Timer Catch Event');
    });

    it('should be disabled after the workflow name is reset', async () => {
      render(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />,
        {wrapper: Wrapper}
      );

      fireEvent.change(screen.getByRole('combobox', {name: 'Workflow'}), {
        target: {value: 'demoProcess'},
      });

      fireEvent.change(
        screen.getByRole('combobox', {name: 'Workflow Version'}),
        {
          target: {value: '3'},
        }
      );

      const flowNode = screen.getByRole('combobox', {
        name: 'Flow Node',
      }) as HTMLInputElement;

      expect(flowNode).not.toHaveAttribute('disabled');

      fireEvent.change(screen.getByRole('combobox', {name: 'Workflow'}), {
        target: {value: ''},
      });

      expect(flowNode).toHaveAttribute('disabled');
    });

    it('should set the state on activityId selection', async () => {
      // given

      filtersStore.setFilter({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        workflow: 'demoProcess',
        version: '2',
      });

      const node = shallow(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={{
            ...DEFAULT_FILTER_CONTROLLED_VALUES,
            workflow: 'demoProcess',
            version: '2',
          }}
        />
      );

      await flushPromises();
      node.update();

      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const selectableFlowNodes = instancesDiagramStore.selectableFlowNodes;
      const activityId = selectableFlowNodes[0].id;
      //when
      // select workflowName, the version is set to the latest
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'handleWorkflowNameChange' does not exist... Remove this comment to see the full error message
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      const instance = node.instance();

      // @ts-expect-error ts-migrate(2339) FIXME: Property 'handleControlledInputChange' does not ex... Remove this comment to see the full error message
      instance.handleControlledInputChange({
        target: {
          value: selectableFlowNodes[0].id,
          name: 'activityId',
        },
      });
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'propagateFilter' does not exist on type ... Remove this comment to see the full error message
      instance.propagateFilter();

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
      expect(node.state().filter.activityId).toEqual(activityId);
    });
  });

  describe('startDate filter', () => {
    it('should render', async () => {
      render(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />,
        {wrapper: Wrapper}
      );

      const startDateField = screen.getByRole('textbox', {
        name: /start date/i,
      }) as HTMLInputElement;

      expect(startDateField.value).toBe('');
      expect(startDateField.placeholder).toBe('Start Date YYYY-MM-DD hh:mm:ss');

      fireEvent.change(startDateField, {target: {value: '1084-10-08'}});
      expect(startDateField.value).toBe('1084-10-08');

      //@ts-expect-error
      expect(filtersStore.state.filter.startDate).toBeUndefined();
      await waitFor(() =>
        //@ts-expect-error
        expect(filtersStore.state.filter.startDate).toBe('1084-10-08')
      );

      fireEvent.change(startDateField, {target: {value: ''}});
      await waitFor(() =>
        //@ts-expect-error
        expect(filtersStore.state.filter.startDate).toBeUndefined()
      );
    });

    it('should be prefilled with the value from props.filter.startDate', async () => {
      render(
        <Filters.WrappedComponent
          {...mockPropsWithInitFilter}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={COMPLETE_FILTER}
        />,
        {wrapper: Wrapper}
      );
      const startDateField = screen.getByRole('textbox', {
        name: /start date/i,
      }) as HTMLInputElement;

      expect(startDateField.value).toEqual('2018-10-08');
    });
  });

  describe('endDate filter', () => {
    it('should render', async () => {
      render(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />,
        {wrapper: Wrapper}
      );

      const endDateField = screen.getByRole('textbox', {
        name: /end date/i,
      }) as HTMLInputElement;

      expect(endDateField.value).toBe('');
      expect(endDateField.placeholder).toBe('End Date YYYY-MM-DD hh:mm:ss');

      fireEvent.change(endDateField, {target: {value: '1084-10-08'}});
      expect(endDateField.value).toBe('1084-10-08');

      //@ts-expect-error
      expect(filtersStore.state.filter.endDate).toBeUndefined();
      await waitFor(() =>
        //@ts-expect-error
        expect(filtersStore.state.filter.endDate).toBe('1084-10-08')
      );

      fireEvent.change(endDateField, {target: {value: ''}});
      await waitFor(() =>
        //@ts-expect-error
        expect(filtersStore.state.filter.endDate).toBeUndefined()
      );
    });

    it('should be prefilled with the value from props.filter.endDate', async () => {
      render(
        <Filters.WrappedComponent
          {...mockPropsWithInitFilter}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={COMPLETE_FILTER}
        />,
        {wrapper: Wrapper}
      );
      const endDateField = screen.getByRole('textbox', {
        name: /end date/i,
      }) as HTMLInputElement;

      expect(endDateField.value).toEqual('2018-10-10');
    });
  });

  describe('variable filter', () => {
    it('should set filter state with variable', async () => {
      render(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />,
        {wrapper: Wrapper}
      );

      const variableName = screen.getByRole('textbox', {
        name: 'Variable',
      }) as HTMLInputElement;
      const variableValue = screen.getByRole('textbox', {
        name: 'Value',
      }) as HTMLInputElement;

      // on valid variable
      fireEvent.change(variableName, {target: {value: 'variableName'}});
      fireEvent.change(variableValue, {target: {value: '{"a": "b"}'}});
      expect(variableName.value).toBe('variableName');
      expect(variableValue.value).toBe('{"a": "b"}');

      //@ts-expect-error
      expect(filtersStore.state.filter.variable).toBe(undefined);
      await waitFor(() =>
        //@ts-expect-error
        expect(filtersStore.state.filter.variable).toEqual({
          name: 'variableName',
          value: '{"a": "b"}',
        })
      );

      //on invalid json
      fireEvent.change(variableName, {target: {value: 'variableName'}});
      fireEvent.change(variableValue, {target: {value: '{{{{'}});
      expect(variableName.value).toBe('variableName');
      expect(variableValue.value).toBe('{{{{');
      await waitFor(() =>
        //@ts-expect-error
        expect(filtersStore.state.filter.variable).toBe(undefined)
      );

      // change back to a valid value
      fireEvent.change(variableValue, {target: {value: '{"a": "b"}'}});
      await waitFor(() =>
        //@ts-expect-error
        expect(filtersStore.state.filter.variable).toEqual({
          name: 'variableName',
          value: '{"a": "b"}',
        })
      );

      // on empty name
      fireEvent.change(variableName, {target: {value: ''}});
      await waitFor(() =>
        //@ts-expect-error
        expect(filtersStore.state.filter.variable).toBe(undefined)
      );

      // change back to a valid name
      fireEvent.change(variableName, {target: {value: 'variableName'}});
      await waitFor(() =>
        //@ts-expect-error
        expect(filtersStore.state.filter.variable).toEqual({
          name: 'variableName',
          value: '{"a": "b"}',
        })
      );

      // on empty value
      fireEvent.change(variableValue, {target: {value: ''}});
      await waitFor(() =>
        //@ts-expect-error
        expect(filtersStore.state.filter.variable).toBe(undefined)
      );
    });
  });

  describe('reset button', () => {
    it('should render enabled reset filters button', () => {
      render(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={COMPLETE_FILTER}
        />,
        {wrapper: Wrapper}
      );

      expect(
        screen.getByRole('button', {name: 'Reset filters'})
      ).not.toHaveAttribute('disabled');
    });

    it('should render disabled reset filters button', () => {
      render(
        <Filters.WrappedComponent
          {...mockPropsWithDefaultFilter}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={{
            ...DEFAULT_FILTER_CONTROLLED_VALUES,
            ...DEFAULT_FILTER,
          }}
        />,
        {wrapper: Wrapper}
      );

      expect(
        screen.getByRole('button', {name: 'Reset filters'})
      ).toHaveAttribute('disabled');
    });

    it('should enable the reset filters button after changing input', async () => {
      render(
        <Filters.WrappedComponent
          {...mockPropsWithDefaultFilter}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={{...DEFAULT_FILTER_CONTROLLED_VALUES, ...DEFAULT_FILTER}}
        />,
        {wrapper: Wrapper}
      );

      expect(
        screen.getByRole('button', {name: 'Reset filters'})
      ).toHaveAttribute('disabled');

      const errorMessageField = screen.getByRole('textbox', {
        name: 'Error Message',
      }) as HTMLInputElement;

      expect(errorMessageField.placeholder).toBe('Error Message');
      expect(errorMessageField.value).toBe('');

      fireEvent.change(errorMessageField, {target: {value: 'abc'}});
      await waitFor(() =>
        //@ts-expect-error
        expect(filtersStore.state.filter.errorMessage).toBe('abc')
      );

      expect(
        screen.getByRole('button', {name: 'Reset filters'})
      ).not.toHaveAttribute('disabled');

      fireEvent.click(screen.getByRole('button', {name: 'Reset filters'}));

      // @ts-expect-error ts-migrate(2339) FIXME: Property 'errorMessage' does not exist on type '{}... Remove this comment to see the full error message
      expect(filtersStore.state.filter.errorMessage).toBe(undefined);
    });

    it('should reset all fields', async () => {
      render(
        /* @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message */
        <Filters.WrappedComponent {...mockProps} filter={COMPLETE_FILTER} />,
        {wrapper: Wrapper}
      );

      const workflow = screen.getByRole('combobox', {
        name: 'Workflow',
      }) as HTMLInputElement;
      const version = screen.getByRole('combobox', {
        name: 'Workflow Version',
      }) as HTMLInputElement;

      const idsField = screen.getByRole('textbox', {
        name: 'Instance Id(s) separated by space or comma',
      }) as HTMLInputElement;
      const errorMessageField = screen.getByRole('textbox', {
        name: 'Error Message',
      }) as HTMLInputElement;

      const startDateField = screen.getByRole('textbox', {
        name: /start date/i,
      }) as HTMLInputElement;

      const endDateField = screen.getByRole('textbox', {
        name: /end date/i,
      }) as HTMLInputElement;
      const flowNode = screen.getByRole('combobox', {
        name: 'Flow Node',
      }) as HTMLInputElement;

      const variableName = screen.getByRole('textbox', {
        name: 'Variable',
      }) as HTMLInputElement;
      const variableValue = screen.getByRole('textbox', {
        name: 'Value',
      }) as HTMLInputElement;

      fireEvent.click(screen.getByRole('button', {name: 'Reset filters'}));
      expect(workflow.value).toBe('');
      expect(version.value).toBe('');
      expect(idsField.value).toBe('');
      expect(errorMessageField.value).toBe('');
      expect(startDateField.value).toBe('');
      expect(endDateField.value).toBe('');
      expect(flowNode.value).toBe('');
      expect(variableName.value).toBe('');
      expect(variableValue.value).toBe('');
    });
  });

  describe('batchOperationId filter', () => {
    it('should render', async () => {
      render(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />,
        {wrapper: Wrapper}
      );

      const operationIdField = screen.getByRole('textbox', {
        name: 'Operation Id',
      }) as HTMLInputElement;

      expect(operationIdField.value).toBe('');
      expect(operationIdField.placeholder).toBe('Operation Id');

      fireEvent.change(operationIdField, {
        target: {value: '8d5aeb73-193b-4bec-a237-8ff71ac1d713'},
      });
      expect(operationIdField.value).toBe(
        '8d5aeb73-193b-4bec-a237-8ff71ac1d713'
      );

      //@ts-expect-error
      expect(filtersStore.state.filter.batchOperationId).toBeUndefined();
      await waitFor(() =>
        //@ts-expect-error
        expect(filtersStore.state.filter.batchOperationId).toBe(
          '8d5aeb73-193b-4bec-a237-8ff71ac1d713'
        )
      );
    });

    it('should be prefilled with the value from props.filter.batchOperationId ', async () => {
      render(
        <Filters.WrappedComponent
          {...mockPropsWithInitFilter}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={COMPLETE_FILTER}
        />,
        {wrapper: Wrapper}
      );
      const operationIdField = screen.getByRole('textbox', {
        name: 'Operation Id',
      }) as HTMLInputElement;

      expect(operationIdField.value).toEqual(
        '8d5aeb73-193b-4bec-a237-8ff71ac1d713'
      );
    });
  });
});
