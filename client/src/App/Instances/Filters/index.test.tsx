/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow, mount} from 'enzyme';
import {act} from 'react-dom/test-utils';
import {createMemoryHistory} from 'history';

import {
  FILTER_TYPES,
  DEFAULT_FILTER,
  DEFAULT_FILTER_CONTROLLED_VALUES,
} from 'modules/constants';
import Button from 'modules/components/Button';
import {
  flushPromises,
  mockWorkflowStatistics,
  mockWorkflowInstances,
} from 'modules/testUtils';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import Filters from './index';
import {FiltersPanel} from './FiltersPanel';
import * as Styled from './styled';
import {
  groupedWorkflowsMock,
  mockProps,
  mockPropsWithEmptyLocationSearch,
  mockPropsWithInitFilter,
  mockPropsWithDefaultFilter,
  COMPLETE_FILTER,
} from './index.setup';

import {DEBOUNCE_DELAY, ALL_VERSIONS_OPTION} from './constants';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {filtersStore} from 'modules/stores/filters';
import {getFlowNodeOptions} from './service';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

jest.mock('./constants');
jest.mock('modules/utils/bpmn');

describe('Filters', () => {
  const locationMock = {pathname: '/instances'};
  const historyMock = createMemoryHistory();
  beforeEach(async () => {
    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
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
    jest.clearAllMocks();
    jest.clearAllTimers();
  });

  afterEach(() => {
    filtersStore.reset();
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
    // given
    const {
      active,
      incidents,
      completed,
      canceled,
    } = DEFAULT_FILTER_CONTROLLED_VALUES;

    const node = mount(
      <ThemeProvider>
        <CollapsablePanelProvider>
          <Filters.WrappedComponent
            {...mockProps}
            // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
            filter={DEFAULT_FILTER_CONTROLLED_VALUES}
          />
        </CollapsablePanelProvider>
      </ThemeProvider>
    );
    const FilterNodes = node.find(Styled.CheckboxGroup);

    // then
    expect(FilterNodes).toHaveLength(2);
    expect(FilterNodes.at(0).prop('type')).toBe(FILTER_TYPES.RUNNING);
    expect(FilterNodes.at(0).prop('filter')).toEqual({active, incidents});
    expect(FilterNodes.at(1).prop('type')).toBe(FILTER_TYPES.FINISHED);
    expect(FilterNodes.at(1).prop('filter')).toEqual({completed, canceled});
  });

  describe('errorMessage filter', () => {
    it('should render an errorMessage field', (done) => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere((n) => n.props().name === 'errorMessage')
        .find('input');

      field.simulate('change', {target: {value: 'asd', name: 'errorMessage'}});

      setTimeout(() => {
        // then
        expect(field.length).toEqual(1);
        expect(field.prop('placeholder')).toEqual('Error Message');
        expect(field.prop('value')).toEqual('');
        // @ts-expect-error ts-migrate(2339) FIXME: Property 'errorMessage' does not exist on type '{}... Remove this comment to see the full error message
        expect(filtersStore.state.filter.errorMessage).toBe('asd');
        done();
      }, DEBOUNCE_DELAY * 2);
    });

    it('should not call onFilterChange before debounce delay', (done) => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere((n) => n.props().name === 'errorMessage');

      field.simulate('change', {target: {value: 'test', name: 'errorMessage'}});

      setTimeout(() => {
        // then
        // @ts-expect-error ts-migrate(2339) FIXME: Property 'errorMessage' does not exist on type '{}... Remove this comment to see the full error message
        expect(filtersStore.state.filter.errorMessage).not.toBe('test');

        done();
      }, DEBOUNCE_DELAY / 2);
    });

    // test behaviour here
    it('should initialize the field with empty value', () => {
      const node = shallow(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
      expect(node.state().filter.errorMessage).toEqual('');
    });

    it('should be prefilled with the value from props.filter.errorMessage ', async () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockPropsWithInitFilter}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere((n) => n.props().name === 'errorMessage');

      // then
      expect(field.props().value).toEqual('This is an error message');
    });

    it('should update state when input receives text', () => {
      const node = shallow(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      // @ts-expect-error ts-migrate(2339) FIXME: Property 'handleControlledInputChange' does not ex... Remove this comment to see the full error message
      node.instance().handleControlledInputChange({
        target: {value: 'error message', name: 'errorMessage'},
      });

      // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
      expect(node.state().filter.errorMessage).toEqual('error message');
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
      jest.useFakeTimers();

      // given
      const target = {name: 'ids', value: '0000000000000001'};
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.Textarea)
        .filterWhere((n) => n.props().name === 'ids');

      // when
      field.simulate('change', {target});

      jest.advanceTimersByTime(DEBOUNCE_DELAY);

      await flushPromises();

      // then
      expect(field).toExist();
      expect(field.prop('value')).toEqual('');
      expect(field.prop('placeholder')).toEqual(
        'Instance Id(s) separated by space or comma'
      );

      // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
      expect(filtersStore.state.filter[target.name]).toBe(target.value);
      jest.useRealTimers();
    });

    it('should initialize the field with empty value', () => {
      const node = shallow(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
      expect(node.state().filter.ids).toEqual('');
    });

    it('should update state when input receives text', () => {
      const node = shallow(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      // @ts-expect-error ts-migrate(2339) FIXME: Property 'handleControlledInputChange' does not ex... Remove this comment to see the full error message
      node.instance().handleControlledInputChange({
        target: {value: 'aa, ab, ac', name: 'ids'},
      });

      // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
      expect(node.state().filter.ids).toEqual('aa, ab, ac');
    });

    it('should be prefilled with the value from props.filter.ids ', async () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockPropsWithInitFilter}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={{
                ...DEFAULT_FILTER_CONTROLLED_VALUES,
                ids: '0000000000000001, 0000000000000002',
              }}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.Textarea)
        .filterWhere((n) => n.props().name === 'ids');

      // then
      expect(field.props().value).toEqual('0000000000000001, 0000000000000002');
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
    it('should render an workflow select field', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'workflow');
      const onChange = field.props().onChange;

      // when
      onChange({target: {value: '', name: 'workflow'}});

      // then
      expect(field.length).toEqual(1);
      expect(field.props().value).toEqual('');
      expect(field.props().placeholder).toEqual('Workflow');
      expect(filtersStore.state.filter).toEqual({});
    });

    it('should render the value from this.props.filter.workflow', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockPropsWithInitFilter}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'workflow');

      // then
      expect(field.props().value).toEqual('demoProcess');
    });

    it('should have values read from this.props.groupedWorkflows', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            {/* @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message */}
            <Filters.WrappedComponent {...mockProps} filter={COMPLETE_FILTER} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'workflow');

      expect(field.props().options).toEqual([
        {value: 'bigVarProcess', label: 'Big variable process'},
        {value: 'eventBasedGatewayProcess', label: 'eventBasedGatewayProcess'},
        {value: 'demoProcess', label: 'New demo process'},
        {value: 'orderProcess', label: 'Order'},
      ]);
    });

    it('should update state with selected option', async () => {
      // given
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'handleWorkflowNameChange' does not exist... Remove this comment to see the full error message
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
      expect(node.state().filter.workflow).toEqual(value);
    });

    // @ts-expect-error ts-migrate(2695) FIXME: Left side of comma operator is unused and has no s... Remove this comment to see the full error message
    if (('should update filter value in instances page', () => {}));
  });

  describe('version filter', () => {
    it('should exist and be disabled by default', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'version');
      const onChange = field.props().onChange;

      // when
      onChange({target: {value: '1'}});

      // then
      expect(field.length).toEqual(1);
      expect(field.props().value).toEqual('');
      expect(field.props().placeholder).toEqual('Workflow Version');
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'version' does not exist on type '{}'.
      expect(filtersStore.state.filter.version).toBe('1');
    });

    it('should render the value from this.props.filter.version', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockPropsWithInitFilter}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={COMPLETE_FILTER}
            />{' '}
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'version');

      // then
      expect(field.props().value).toEqual('2');
    });

    it('should display the latest version of a selected workflowName', async () => {
      // given
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const workflowField = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'workflow');
      //when
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      const field = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'version');
      // then
      expect(field.props().value).toEqual(
        String(groupedWorkflowsMock[0].workflows[0].version)
      );
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'version' does not exist on type '{}'.
      expect(filtersStore.state.filter.version).toEqual(
        String(groupedWorkflowsMock[0].workflows[0].version)
      );
    });

    it('should display an all versions option', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const workflowField = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'workflow');
      //when
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      const options = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'version')
        .props().options;

      // then
      expect(options[0].label).toEqual('Version 3');
      expect(options[options.length - 1].value).toEqual(ALL_VERSIONS_OPTION);
      expect(options[options.length - 1].label).toEqual('All versions');
      // groupedWorkflowsMock.workflows.length + 1 (All versions)
      expect(options.length).toEqual(4);
    });

    it('should not allow the selection of the first option', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const workflowField = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'workflow');

      const versionField = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'version');

      //when
      // select workflowName, the version is set to the latest
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      // select WorkflowVersion option, 1st
      versionField.prop('onChange')({target: {value: ''}});
      node.update();

      // then
      // should keep the last version option selected
      expect(
        node
          .find(Styled.Select)
          .filterWhere((n) => n.props().name === 'version')
          .props().value
      ).toEqual(String(groupedWorkflowsMock[0].workflows[0].version));
      // should update the workflow in Instances
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'version' does not exist on type '{}'.
      expect(filtersStore.state.filter.version).toBe('3');
    });

    it('should reset after a the workflowName field is also reseted ', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const workflowField = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'workflow');

      //when
      // select workflowName, the version is set to the latest
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      // select WorkflowVersion option, 1st
      workflowField.prop('onChange')({target: {value: ''}});
      node.update();

      // then
      // should keep the last version option selected
      expect(
        node
          .find(Styled.Select)
          .filterWhere((n) => n.props().name === 'version')
          .props().value
      ).toEqual('');
      expect(
        node
          .find(Styled.Select)
          .filterWhere((n) => n.props().name === 'workflow')
          .props().value
      ).toEqual('');
    });

    it('should set filter state when a workflow version is selected', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      // select workflowName, the version is set to the latest
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'handleWorkflowNameChange' does not exist... Remove this comment to see the full error message
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'workflow' does not exist on type '{}'.
      expect(filtersStore.state.filter.workflow).toBe('demoProcess');
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'version' does not exist on type '{}'.
      expect(filtersStore.state.filter.version).toBe('3');
    });

    it('should set filter state when all workflow versions are selected', async () => {
      const workflowName = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      // select workflowName, the version is set to the latest
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'handleWorkflowNameChange' does not exist... Remove this comment to see the full error message
      node.instance().handleWorkflowNameChange({target: {value: workflowName}});
      node.update();
      node
        .instance()
        // @ts-expect-error ts-migrate(2339) FIXME: Property 'handleWorkflowVersionChange' does not ex... Remove this comment to see the full error message
        .handleWorkflowVersionChange({target: {value: ALL_VERSIONS_OPTION}});

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'workflow' does not exist on type '{}'.
      expect(filtersStore.state.filter.workflow).toBe(workflowName);
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'version' does not exist on type '{}'.
      expect(filtersStore.state.filter.version).toBe(ALL_VERSIONS_OPTION);
    });
  });

  describe('selectable FlowNode filter', () => {
    it('should exist and be disabled by default', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'activityId');
      const onChange = field.props().onChange;

      //when
      onChange({target: {value: '', name: 'activityId'}});
      // then
      expect(field.length).toEqual(1);
      expect(field.props().value).toEqual('');
      expect(field.props().placeholder).toEqual('Flow Node');
      expect(field.props().disabled).toBe(true);
      expect(filtersStore.state.filter).toEqual({});
    });

    it('should render the value from this.props.filter.activityId', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockPropsWithInitFilter}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'activityId');

      // then
      expect(field.props().value).toEqual('4');
    });

    it('should be disabled if All versions is selected', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const workflowField = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'workflow');
      const versionField = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'version');

      //when
      // select workflowName, the version is set to the latest
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      versionField.prop('onChange')({target: {value: ALL_VERSIONS_OPTION}});
      node.update();

      const field = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'activityId');

      // then
      expect(field.props().disabled).toEqual(true);
    });

    it('should not be disabled when a version is selected', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const workflowField = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'workflow');
      const versionField = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'version');

      //when
      // select workflowName, the version is set to the latest
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      versionField.prop('onChange')({
        target: {value: groupedWorkflowsMock[0].workflows[0].version},
      });
      node.update();

      const field = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'activityId');

      // then
      expect(field.props().disabled).toEqual(false);
      expect(field.props().value).toEqual('');
    });

    it('should render selectable flow nodes', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      filtersStore.setFilter({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        workflow: 'demoProcess',
        version: '2',
      });
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={{
                ...DEFAULT_FILTER_CONTROLLED_VALUES,
                workflow: 'demoProcess',
                version: '2',
              }}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      await flushPromises();
      node.update();

      const workflowField = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'workflow');

      //when
      // select workflowName, the version is set to the latest
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      const field = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'activityId');

      const selectableFlowNodes = getFlowNodeOptions(
        instancesDiagramStore.selectableFlowNodes
      );

      expect(field.props().options[0].value).toEqual(
        selectableFlowNodes[0].value
      );
      expect(field.props().options[1].value).toEqual(
        selectableFlowNodes[1].value
      );
    });

    it('should render the selectable flow nodes on the correct order', async () => {
      filtersStore.setFilter({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        workflow: 'demoProcess',
        version: '2',
      });
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={{
                ...DEFAULT_FILTER_CONTROLLED_VALUES,
                workflow: 'demoProcess',
                version: '2',
              }}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      await flushPromises();
      node.update();

      const field = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'activityId');

      expect(field.props().options[0].label).toEqual('End Event');
      expect(field.props().options[1].label).toEqual('Exclusive Gateway');
      expect(field.props().options[2].label).toEqual('Message Catch Event');
      expect(field.props().options[3].label).toEqual('Parallel Gateway');
      expect(field.props().options[4].label).toEqual('task D');
      expect(field.props().options[5].label).toEqual('Timer Catch Event');
    });

    it('should be disabled after the workflow name is reset', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const workflowField = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'workflow');

      //when
      // select workflowName, the version is set to the latest
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      workflowField.prop('onChange')({target: {value: ''}});
      node.update();

      const field = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'activityId');

      // then
      expect(field.props().disabled).toEqual(true);
    });

    it('should display a list of activity ids', async () => {
      // given

      filtersStore.setFilter({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        workflow: 'demoProcess',
        version: '2',
      });

      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={{
                ...DEFAULT_FILTER_CONTROLLED_VALUES,
                workflow: 'demoProcess',
                version: '2',
              }}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      await flushPromises();
      node.update();

      const field = node
        .find(Styled.Select)
        .filterWhere((n) => n.props().name === 'activityId');

      // then
      expect(field.props().options.length).toEqual(6);
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
    it('should exist', async () => {
      jest.useFakeTimers();

      // given
      const target = {value: '1084-10-08', name: 'startDate'};
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere((n) => n.props().name === 'startDate')
        .find('input');

      field.simulate('change', {target});

      jest.advanceTimersByTime(DEBOUNCE_DELAY);

      await flushPromises();

      // then
      expect(field.length).toEqual(1);
      expect(field.props().placeholder).toEqual(
        'Start Date YYYY-MM-DD hh:mm:ss'
      );
      expect(field.props().value).toEqual('');
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'startDate' does not exist on type '{}'.
      expect(filtersStore.state.filter.startDate).toBe('1084-10-08');
      jest.useRealTimers();
    });

    it('should be prefilled with the value from props.filter.startDate', async () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockPropsWithInitFilter}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere((n) => n.props().name === 'startDate');

      // then
      expect(field.props().value).toEqual('2018-10-08');
    });

    //change without implementation
    it('should update the state with new value', async () => {
      const node = shallow(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'handleControlledInputChange' does not ex... Remove this comment to see the full error message
      node.instance().handleControlledInputChange({
        target: {value: '2009-01-25 10:23:01', name: 'startDate'},
      });
      node.update();

      // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
      expect(node.state().filter.startDate).toEqual('2009-01-25 10:23:01');
    });

    it('should update the filters in Instances page', async () => {
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
        target: {value: '2009-01-25', name: 'startDate'},
      });
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'propagateFilter' does not exist on type ... Remove this comment to see the full error message
      instance.propagateFilter();
      node.update();

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'startDate' does not exist on type '{}'.
      expect(filtersStore.state.filter.startDate).toBe('2009-01-25');
    });

    it('should send null values for empty start dates', async () => {
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
        target: {value: '', name: 'startDate'},
      });
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'propagateFilter' does not exist on type ... Remove this comment to see the full error message
      instance.propagateFilter();
      node.update();

      // then
      expect(filtersStore.state.filter).toEqual({});
    });
  });

  describe('endDate filter', () => {
    it('should exist', async () => {
      jest.useFakeTimers();

      // given
      const target = {value: '1984-10-08', name: 'endDate'};
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere((n) => n.props().name === 'endDate')
        .find('input');

      field.simulate('change', {target});

      jest.advanceTimersByTime(DEBOUNCE_DELAY);

      await flushPromises();

      expect(field.length).toEqual(1);
      expect(field.props().name).toEqual('endDate');
      expect(field.props().placeholder).toEqual('End Date YYYY-MM-DD hh:mm:ss');
      expect(field.props().value).toEqual('');
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'endDate' does not exist on type '{}'.
      expect(filtersStore.state.filter.endDate).toBe('1984-10-08');
      jest.useRealTimers();
    });

    it('should be prefilled with the value from props.filter.endDate', async () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockPropsWithInitFilter}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      //when
      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere((n) => n.props().name === 'endDate');

      // then
      expect(field.props().value).toBe('2018-10-10');
    });

    // change
    it('should update the state with new value', async () => {
      const node = shallow(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'handleControlledInputChange' does not ex... Remove this comment to see the full error message
      node.instance().handleControlledInputChange({
        target: {value: '2009-01-25', name: 'endDate'},
      });
      node.update();

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
      expect(node.state().filter.endDate).toBe('2009-01-25');
    });

    it('should update the filters in Instances page', async () => {
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
        target: {value: '2009-01-25', name: 'endDate'},
      });
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'propagateFilter' does not exist on type ... Remove this comment to see the full error message
      instance.propagateFilter();
      node.update();

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
      expect(node.state().filter.endDate).toBe('2009-01-25');
    });
  });

  describe('variable filter', () => {
    let node: any;

    const triggerVariableChange = async ({node, name, value}: any) => {
      const nameTarget = {target: {name: 'name', value: name}};
      const valueTarget = {target: {name: 'value', value: value}};

      const nameInput = node.find('input[data-testid="nameInput"]');
      const valueInput = node.find('input[data-testid="valueInput"]');

      nameInput.simulate('change', nameTarget);
      valueInput.simulate('change', valueTarget);

      jest.advanceTimersByTime(DEBOUNCE_DELAY);
      await flushPromises();
    };

    beforeEach(() => {
      jest.useFakeTimers();

      node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
    });

    afterEach(() => {
      jest.useRealTimers();
    });

    it('should call onFilterChange on valid variable', async () => {
      // given
      const variable = {name: 'variableName', value: '{"a": "b"}'};

      // when

      await triggerVariableChange({
        node,
        ...variable,
      });

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'variable' does not exist on type '{}'.
      expect(filtersStore.state.filter.variable).toEqual(variable);
    });

    it('should set filter state with empty object (on invalid JSON value)', async () => {
      // given
      const variable = {name: 'variableName', value: '{{{{'};

      // when
      await act(async () => {
        await triggerVariableChange({
          node,
          ...variable,
        });
      });

      // then
      expect(filtersStore.state.filter).toEqual({});
    });

    it('should set filter state with empty object (on empty name)', async () => {
      // given
      const variable = {name: '', value: '{"a": "b"}'};

      // when
      await act(async () => {
        await triggerVariableChange({
          node,
          ...variable,
        });
      });

      // then
      expect(filtersStore.state.filter).toEqual({});
    });

    it('should set filter state with empty object (on empty value)', async () => {
      // given
      const variable = {name: 'myVariable', value: ''};

      // when
      await act(async () => {
        await triggerVariableChange({
          node,
          ...variable,
        });
      });

      // then
      expect(filtersStore.state.filter).toEqual({});
    });

    it('should set filter state with empty object (on empty name and value)', async () => {
      // given
      const variable = {name: '', value: ''};

      // when
      await triggerVariableChange({
        node,
        ...variable,
      });

      // then
      expect(filtersStore.state.filter).toEqual({});
    });
  });

  describe('reset button', () => {
    it('should render the reset filters button', () => {
      // given filter is different from DEFAULT_FILTER_CONTROLLED_VALUES
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            {/* @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message */}
            <Filters.WrappedComponent {...mockProps} filter={COMPLETE_FILTER} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const ResetButtonNode = node.find(Button);
      // then
      expect(ResetButtonNode.text()).toBe('Reset Filters');
      expect(ResetButtonNode).toHaveLength(1);
      expect(ResetButtonNode.prop('disabled')).toBe(false);
    });

    it('should render the disabled reset filters button', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockPropsWithDefaultFilter}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={{
                ...DEFAULT_FILTER_CONTROLLED_VALUES,
                ...DEFAULT_FILTER,
              }}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const ResetButtonNode = node.find(Button);

      // then
      expect(ResetButtonNode).toHaveLength(1);
      expect(ResetButtonNode.prop('disabled')).toBe(true);
    });

    it('should render the reset filters button after changing input', async () => {
      jest.useFakeTimers();

      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={{...DEFAULT_FILTER_CONTROLLED_VALUES, ...DEFAULT_FILTER}}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const errorMessageInput = node.find('input[name="errorMessage"]');

      // when
      errorMessageInput.simulate('change', {
        target: {value: 'abc', name: 'errorMessage'},
      });
      jest.advanceTimersByTime(DEBOUNCE_DELAY);
      await flushPromises();

      // @ts-expect-error ts-migrate(2339) FIXME: Property 'errorMessage' does not exist on type '{}... Remove this comment to see the full error message
      expect(filtersStore.state.filter.errorMessage).toBe('abc');
      const ResetButtonNode = node.find(Button);
      ResetButtonNode.simulate('click');

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'errorMessage' does not exist on type '{}... Remove this comment to see the full error message
      expect(filtersStore.state.filter.errorMessage).toBe(undefined);

      jest.useRealTimers();
    });

    it('should reset all fields', async () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            {/* @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message */}
            <Filters.WrappedComponent {...mockProps} filter={COMPLETE_FILTER} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const ResetButtonNode = node.find(Button);

      // click reset filters
      ResetButtonNode.simulate('click');
      node.update();

      // then
      expect(node.find('select[name="workflow"]').get(0).props.value).toBe('');
      expect(node.find('select[name="version"]').get(0).props.value).toBe('');
      expect(node.find('textarea[name="ids"]').get(0).props.value).toBe('');
      expect(node.find('input[name="errorMessage"]').get(0).props.value).toBe(
        ''
      );
      expect(node.find('input[name="startDate"]').get(0).props.value).toBe('');
      expect(node.find('input[name="endDate"]').get(0).props.value).toBe('');
      expect(node.find('select[name="activityId"]').get(0).props.value).toBe(
        ''
      );
      expect(node.find('input[name="name"]').get(0).props.value).toBe('');
      expect(node.find('input[name="value"]').get(0).props.value).toBe('');
    });
  });

  describe('batchOperationId filter', () => {
    beforeEach(() => {
      jest.useFakeTimers();
    });
    afterEach(() => {
      jest.useRealTimers();
    });
    it('should render a batchOperationId field', async () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere((n) => n.props().name === 'batchOperationId')
        .find('input');

      field.simulate('change', {
        target: {
          value: '8d5aeb73-193b-4bec-a237-8ff71ac1d713',
          name: 'batchOperationId',
        },
      });

      jest.advanceTimersByTime(DEBOUNCE_DELAY);

      await flushPromises();

      expect(field.length).toEqual(1);
      expect(field.prop('placeholder')).toEqual('Operation Id');
      expect(field.prop('value')).toEqual('');
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'batchOperationId' does not exist on type... Remove this comment to see the full error message
      expect(filtersStore.state.filter.batchOperationId).toBe(
        '8d5aeb73-193b-4bec-a237-8ff71ac1d713'
      );
    });

    it('should not set batch operation id before debounce delay', async () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockProps}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere((n) => n.props().name === 'batchOperationId')
        .find('input');

      field.simulate('change', {
        target: {value: 'asd', name: 'batchOperationId'},
      });

      await flushPromises();

      // @ts-expect-error ts-migrate(2339) FIXME: Property 'batchOperationId' does not exist on type... Remove this comment to see the full error message
      expect(filtersStore.state.filter.batchOperationId).not.toBe('asd');
    });

    // test behaviour here
    it('should initialize the field with empty value', () => {
      const node = shallow(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
      expect(node.state().filter.batchOperationId).toEqual('');
    });

    it('should be prefilled with the value from props.filter.batchOperationId ', async () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              {...mockPropsWithInitFilter}
              // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere((n) => n.props().name === 'batchOperationId');

      // then
      expect(field.props().value).toEqual(
        '8d5aeb73-193b-4bec-a237-8ff71ac1d713'
      );
    });

    it('should update state when input receives text', () => {
      const node = shallow(
        <Filters.WrappedComponent
          {...mockProps}
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'filter' does not exist on type 'Intrinsi... Remove this comment to see the full error message
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      // @ts-expect-error ts-migrate(2339) FIXME: Property 'handleControlledInputChange' does not ex... Remove this comment to see the full error message
      node.instance().handleControlledInputChange({
        target: {value: 'batch operation id', name: 'batchOperationId'},
      });

      // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type 'Readonly... Remove this comment to see the full error message
      expect(node.state().filter.batchOperationId).toEqual(
        'batch operation id'
      );
    });

    it('should set filter state with the right batch operation id', async () => {
      // given
      const batchOperationId = '8d5aeb73-193b-4bec-a237-8ff71ac1d713';
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
        target: {value: batchOperationId, name: 'batchOperationId'},
      });
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'waitForTimer' does not exist on type 'Co... Remove this comment to see the full error message
      instance.waitForTimer(instance.propagateFilter);

      jest.advanceTimersByTime(DEBOUNCE_DELAY);

      await flushPromises();

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'batchOperationId' does not exist on type... Remove this comment to see the full error message
      expect(filtersStore.state.filter.batchOperationId).toBe(batchOperationId);
    });

    it('should set filter state with empty object', async () => {
      // given
      const emptyBatchOperationId = '';
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
        target: {value: emptyBatchOperationId, name: 'batchOperationId'},
      });
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'waitForTimer' does not exist on type 'Co... Remove this comment to see the full error message
      instance.waitForTimer(instance.propagateFilter);
      jest.advanceTimersByTime(DEBOUNCE_DELAY);

      await flushPromises();

      // then
      expect(filtersStore.state.filter).toEqual({});
    });
  });
});
