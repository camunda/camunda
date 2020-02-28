/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow, mount} from 'enzyme';
import {act} from 'react-dom/test-utils';

import {
  FILTER_TYPES,
  DEFAULT_FILTER,
  DEFAULT_FILTER_CONTROLLED_VALUES
} from 'modules/constants';
import Button from 'modules/components/Button';
import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';
import * as api from 'modules/api/instances/instances';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import Filters from './Filters';
import * as Styled from './styled';
import {
  groupedWorkflowsMock,
  workflows,
  mockProps,
  mockPropsWithSelectableFlowNodes,
  mockPropsWithInitFilter,
  mockPropsWithDefaultFilter,
  COMPLETE_FILTER
} from './Filters.setup';

import {DEBOUNCE_DELAY, ALL_VERSIONS_OPTION} from './constants';

jest.mock('./constants');
jest.mock('modules/utils/bpmn');

api.fetchGroupedWorkflows = mockResolvedAsyncFn(groupedWorkflowsMock);

describe('Filters', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.clearAllTimers();
  });

  it('should render with the right initial state', () => {
    // given
    const node = shallow(
      <Filters.WrappedComponent
        groupedWorkflows={workflows}
        {...mockProps}
        filter={DEFAULT_FILTER_CONTROLLED_VALUES}
      />
    );

    // then
    expect(node.state().filter.activityId).toEqual('');
    expect(node.state().filter.workflow).toEqual('');
    expect(node.state().filter.version).toEqual('');
    expect(node.state().filter.startDate).toEqual('');
    expect(node.state().filter.endDate).toEqual('');
    expect(node.state().filter.ids).toEqual('');
    expect(node.state().filter.errorMessage).toEqual('');
    expect(node.state().filter.batchOperationId).toEqual('');
    expect(node.state().filter.variable).toEqual({name: '', value: ''});
  });

  it('should render with prefilled input fields', () => {
    // given
    const node = shallow(
      <Filters.WrappedComponent
        groupedWorkflows={workflows}
        {...mockPropsWithInitFilter}
        filter={COMPLETE_FILTER}
      />
    );

    // then
    expect(node.state().filter.activityId).toEqual(COMPLETE_FILTER.activityId);
    expect(node.state().filter.workflow).toEqual(COMPLETE_FILTER.workflow);
    expect(node.state().filter.version).toEqual(COMPLETE_FILTER.version);
    expect(node.state().filter.startDate).toEqual(COMPLETE_FILTER.startDate);
    expect(node.state().filter.endDate).toEqual(COMPLETE_FILTER.endDate);
    expect(node.state().filter.ids).toEqual(COMPLETE_FILTER.ids);
    expect(node.state().filter.errorMessage).toEqual(
      COMPLETE_FILTER.errorMessage
    );
    expect(node.state().filter.variable).toEqual(COMPLETE_FILTER.variable);
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
      canceled
    } = DEFAULT_FILTER_CONTROLLED_VALUES;

    const node = mount(
      <ThemeProvider>
        <CollapsablePanelProvider>
          <Filters.WrappedComponent
            groupedWorkflows={workflows}
            {...mockProps}
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
    it('should render an errorMessage field', done => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere(n => n.props().name === 'errorMessage')
        .find('input');

      field.simulate('change', {target: {value: 'asd', name: 'errorMessage'}});

      setTimeout(() => {
        // then
        expect(field.length).toEqual(1);
        expect(field.prop('placeholder')).toEqual('Error Message');
        expect(field.prop('value')).toEqual('');
        expect(mockProps.onFilterChange).toHaveBeenCalledWith({
          errorMessage: 'asd'
        });

        done();
      }, DEBOUNCE_DELAY * 2);
    });

    it('should not call onFilterChange before debounce delay', done => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere(n => n.props().name === 'errorMessage');

      field.simulate('change', {target: {value: '', name: 'errorMessage'}});

      setTimeout(() => {
        // then
        expect(mockProps.onFilterChange).not.toHaveBeenCalled();

        done();
      }, DEBOUNCE_DELAY / 2);
    });

    // test behaviour here
    it('should initialize the field with empty value', () => {
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      expect(node.state().filter.errorMessage).toEqual('');
    });

    it('should be prefilled with the value from props.filter.errorMessage ', async () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockPropsWithInitFilter}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere(n => n.props().name === 'errorMessage');

      // then
      expect(field.props().value).toEqual('This is an error message');
    });

    it('should update state when input receives text', () => {
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      node.instance().handleControlledInputChange({
        target: {value: 'error message', name: 'errorMessage'}
      });

      expect(node.state().filter.errorMessage).toEqual('error message');
    });

    it('should call onFilterChange with the right error message', done => {
      // given
      const errorMessage = 'lorem ipsum';
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );
      const instance = node.instance();

      //when
      instance.handleControlledInputChange({
        target: {value: errorMessage, name: 'errorMessage'}
      });
      instance.waitForTimer(instance.propagateFilter);

      setTimeout(() => {
        // then
        expect(mockProps.onFilterChange).toHaveBeenCalledWith({
          errorMessage
        });
        done();
      }, DEBOUNCE_DELAY * 2);
    });

    it('should call onFilterChange with empty object', done => {
      // given
      const emptyErrorMessage = '';
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );
      const instance = node.instance();

      //when
      instance.handleControlledInputChange({
        target: {value: emptyErrorMessage, name: 'errorMessage'}
      });
      instance.waitForTimer(instance.propagateFilter);

      setTimeout(() => {
        // then
        expect(mockProps.onFilterChange).toHaveBeenCalledWith({});
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
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.Textarea)
        .filterWhere(n => n.props().name === 'ids');

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
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({
        [target.name]: target.value
      });
    });

    it('should initialize the field with empty value', () => {
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      expect(node.state().filter.ids).toEqual('');
    });

    it('should update state when input receives text', () => {
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      node.instance().handleControlledInputChange({
        target: {value: 'aa, ab, ac', name: 'ids'}
      });

      expect(node.state().filter.ids).toEqual('aa, ab, ac');
    });

    it('should be prefilled with the value from props.filter.ids ', async () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockPropsWithInitFilter}
              filter={{
                ...DEFAULT_FILTER_CONTROLLED_VALUES,
                ids: '0000000000000001, 0000000000000002'
              }}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.Textarea)
        .filterWhere(n => n.props().name === 'ids');

      // then
      expect(field.props().value).toEqual('0000000000000001, 0000000000000002');
    });

    it('should call onFilterChange with the right instance ids', () => {
      const instanceIds = '0000000000000001, 0000000000000002';
      // given
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      const instance = node.instance();
      instance.handleControlledInputChange({
        target: {value: instanceIds, name: 'ids'}
      });
      instance.propagateFilter();

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({
        ids: instanceIds
      });
    });

    it('should call onFilterChange with an empty object', () => {
      // given
      // user blurs without writing
      const emptyInstanceIds = '';
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      const instance = node.instance();
      instance.handleControlledInputChange({
        target: {value: emptyInstanceIds, name: 'ids'}
      });
      instance.propagateFilter();

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({});
    });
  });

  describe('workflow filter', () => {
    it('should render an workflow select field', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');
      const onChange = field.props().onChange;

      // when
      onChange({target: {value: '', name: 'workflow'}});

      // then
      expect(field.length).toEqual(1);
      expect(field.props().value).toEqual('');
      expect(field.props().placeholder).toEqual('Workflow');
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({});
    });

    it('should render the value from this.props.filter.workflow', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockPropsWithInitFilter}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');

      // then
      expect(field.props().value).toEqual('demoProcess');
    });

    it('should have values read from this.props.groupedWorkflows', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockProps}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');

      expect(field.props().options).toEqual([
        {value: 'demoProcess', label: 'New demo process'},
        {value: 'orderProcess', label: 'Order'}
      ]);
    });

    it('should update state with selected option', async () => {
      // given
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      // then
      expect(node.state().filter.workflow).toEqual(value);
    });

    if (('should update filter value in instances page', () => {}));
  });

  describe('version filter', () => {
    it('should exist and be disabled by default', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'version');
      const onChange = field.props().onChange;

      // when
      onChange({target: {value: '1'}});

      // then
      expect(field.length).toEqual(1);
      expect(field.props().value).toEqual('');
      expect(field.props().placeholder).toEqual('Workflow Version');
      expect(mockProps.onFilterChange).toHaveBeenCalled();
    });

    it('should render the value from this.props.filter.version', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockPropsWithInitFilter}
              filter={COMPLETE_FILTER}
            />{' '}
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'version');

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
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const workflowField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');
      //when
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'version');
      // then
      expect(field.props().value).toEqual(
        String(groupedWorkflowsMock[0].workflows[0].version)
      );
      expect(mockProps.onFilterChange.mock.calls[0][0].version).toEqual(
        String(groupedWorkflowsMock[0].workflows[0].version)
      );
    });

    it('should display an all versions option', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const workflowField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');
      //when
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      const options = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'version')
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
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const workflowField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');

      const versionField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'version');

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
          .filterWhere(n => n.props().name === 'version')
          .props().value
      ).toEqual(String(groupedWorkflowsMock[0].workflows[0].version));
      // should update the workflow in Instances
      expect(mockProps.onFilterChange.mock.calls.length).toEqual(1);
    });

    it('should reset after a the workflowName field is also reseted ', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const workflowField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');

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
          .filterWhere(n => n.props().name === 'version')
          .props().value
      ).toEqual('');
      expect(
        node
          .find(Styled.Select)
          .filterWhere(n => n.props().name === 'workflow')
          .props().value
      ).toEqual('');
    });

    it('should call onFilterChange when a workflow version is selected', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      // select workflowName, the version is set to the latest
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({
        workflow: 'demoProcess',
        version: '3'
      });
    });

    it('should call onFilterChange when all workflow versions are selected', async () => {
      const workflowName = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      // select workflowName, the version is set to the latest
      node.instance().handleWorkflowNameChange({target: {value: workflowName}});
      node.update();
      node
        .instance()
        .handleWorkflowVersionChange({target: {value: ALL_VERSIONS_OPTION}});

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({
        version: ALL_VERSIONS_OPTION,
        workflow: workflowName
      });
    });
  });

  describe('selectable FlowNode filter', () => {
    it('should exist and be disabled by default', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'activityId');
      const onChange = field.props().onChange;

      //when
      onChange({target: {value: '', name: 'activityId'}});
      // then
      expect(field.length).toEqual(1);
      expect(field.props().value).toEqual('');
      expect(field.props().placeholder).toEqual('Flow Node');
      expect(field.props().disabled).toBe(true);
      expect(field.props().options.length).toBe(0);
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({});
    });

    it('should render the value from this.props.filter.activityId', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockPropsWithInitFilter}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'activityId');

      // then
      expect(field.props().value).toEqual('4');
    });

    it('should be disabled if All versions is selected', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const workflowField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');
      const versionField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'version');

      //when
      // select workflowName, the version is set to the latest
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      versionField.prop('onChange')({target: {value: ALL_VERSIONS_OPTION}});
      node.update();

      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'activityId');

      // then
      expect(field.props().disabled).toEqual(true);
    });

    it('should not be disabled when a version is selected', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const workflowField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');
      const versionField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'version');

      //when
      // select workflowName, the version is set to the latest
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      versionField.prop('onChange')({
        target: {value: groupedWorkflowsMock[0].workflows[0].version}
      });
      node.update();

      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'activityId');

      // then
      expect(field.props().disabled).toEqual(false);
      expect(field.props().value).toEqual('');
    });

    it('should read the options from this.props.selectableFlowNodes', () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockPropsWithSelectableFlowNodes}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const workflowField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');

      //when
      // select workflowName, the version is set to the latest
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'activityId');

      expect(field.props().options[0].value).toEqual(
        mockPropsWithSelectableFlowNodes.selectableFlowNodes[0].id
      );
      expect(field.props().options[1].value).toEqual(
        mockPropsWithSelectableFlowNodes.selectableFlowNodes[1].id
      );

      // Use name for the label if it exists
      expect(field.props().options[0].label).toEqual(
        mockPropsWithSelectableFlowNodes.selectableFlowNodes[0].name
      );

      // Use a fall-back name for the label if it the flowNode doesn't have one
      expect(field.props().options[1].label).toEqual('Unnamed End Event');
    });

    it('should split the selectable flowNode options by named/unnamed & sort alphabetically', () => {
      const unsortedSelectableFlowNodes = [
        {id: 'TaskC', $type: 'bpmn:StartEvent', name: 'task C'},
        {id: 'TaskA', $type: 'bpmn:StartEvent', name: 'task A'},
        {id: 'TaskD', $type: 'bpmn:AEndEvent'},
        {id: 'TaskE', $type: 'bpmn:CEndEvent'}
      ];

      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockPropsWithSelectableFlowNodes}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
              selectableFlowNodes={unsortedSelectableFlowNodes}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'activityId');

      expect(field.props().options[0].label).toEqual(
        unsortedSelectableFlowNodes[1].name
      );
      expect(field.props().options[1].label).toEqual(
        unsortedSelectableFlowNodes[0].name
      );
      expect(field.props().options[2].label).toEqual('Unnamed A End Event');
      expect(field.props().options[3].label).toEqual('Unnamed C End Event');
    });

    it('should be disabled after the workflow name is reseted', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const workflowField = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'workflow');

      //when
      // select workflowName, the version is set to the latest
      workflowField.prop('onChange')({target: {value: value}});
      node.update();

      workflowField.prop('onChange')({target: {value: ''}});
      node.update();

      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'activityId');

      // then
      expect(field.props().disabled).toEqual(true);
      expect(field.props().options.length).toEqual(0);
    });

    it('should display a list of activity ids', async () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockPropsWithSelectableFlowNodes}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.Select)
        .filterWhere(n => n.props().name === 'activityId');

      // then
      expect(field.props().options.length).toEqual(2);
    });

    it('should set the state on activityId selection', async () => {
      // given

      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const activityId =
        mockPropsWithSelectableFlowNodes.selectableFlowNodes[0].id;

      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockPropsWithSelectableFlowNodes}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      // select workflowName, the version is set to the latest
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      const instance = node.instance();

      instance.handleControlledInputChange({
        target: {
          value: mockPropsWithSelectableFlowNodes.selectableFlowNodes[0].id,
          name: 'activityId'
        }
      });
      instance.propagateFilter();

      // then
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
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere(n => n.props().name === 'startDate')
        .find('input');

      field.simulate('change', {target});

      jest.advanceTimersByTime(DEBOUNCE_DELAY);

      await flushPromises();

      // then
      expect(field.length).toEqual(1);
      expect(field.props().placeholder).toEqual(
        'Start Date yyyy-mm-dd hh:mm:ss'
      );
      expect(field.props().value).toEqual('');
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({
        startDate: '1084-10-08'
      });
    });

    it('should be prefilled with the value from props.filter.startDate', async () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockPropsWithInitFilter}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere(n => n.props().name === 'startDate');

      // then
      expect(field.props().value).toEqual('2018-10-08');
    });

    //change without implementation
    it('should update the state with new value', async () => {
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      node.instance().handleControlledInputChange({
        target: {value: '2009-01-25 10:23:01', name: 'startDate'}
      });
      node.update();

      expect(node.state().filter.startDate).toEqual('2009-01-25 10:23:01');
    });

    it('should update the filters in Instances page', async () => {
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      const instance = node.instance();
      instance.handleControlledInputChange({
        target: {value: '2009-01-25', name: 'startDate'}
      });
      instance.propagateFilter();
      node.update();

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalled();
      expect(mockProps.onFilterChange.mock.calls[0][0].startDate).toBe(
        '2009-01-25'
      );
    });

    it('should send null values for empty start dates', async () => {
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      const instance = node.instance();
      instance.handleControlledInputChange({
        target: {value: '', name: 'startDate'}
      });
      instance.propagateFilter();
      node.update();

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({});
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
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere(n => n.props().name === 'endDate')
        .find('input');

      field.simulate('change', {target});

      jest.advanceTimersByTime(DEBOUNCE_DELAY);

      await flushPromises();

      expect(field.length).toEqual(1);
      expect(field.props().name).toEqual('endDate');
      expect(field.props().placeholder).toEqual('End Date yyyy-mm-dd hh:mm:ss');
      expect(field.props().value).toEqual('');
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({
        endDate: '1984-10-08'
      });
    });

    it('should be prefilled with the value from props.filter.endDate', async () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockPropsWithInitFilter}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      //when
      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere(n => n.props().name === 'endDate');

      // then
      expect(field.props().value).toEqual('2018-10-10');
    });

    // change
    it('should update the state with new value', async () => {
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      node.instance().handleControlledInputChange({
        target: {value: '2009-01-25', name: 'endDate'}
      });
      node.update();

      // then
      expect(node.state().filter.endDate).toEqual('2009-01-25');
    });

    it('should update the filters in Instances page', async () => {
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      const instance = node.instance();
      instance.handleControlledInputChange({
        target: {value: '2009-01-25', name: 'endDate'}
      });
      instance.propagateFilter();
      node.update();

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalled();
      expect(mockProps.onFilterChange.mock.calls[0][0].endDate).toBe(
        '2009-01-25'
      );
    });
  });

  describe('variable filter', () => {
    let node;

    const triggerVariableChange = async ({node, name, value}) => {
      const nameTarget = {target: {name: 'name', value: name}};
      const valueTarget = {target: {name: 'value', value: value}};

      const nameInput = node.find('input[data-test="nameInput"]');
      const valueInput = node.find('input[data-test="valueInput"]');

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
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
    });

    it('should call onFilterChange on valid variable', async () => {
      // given
      const variable = {name: 'variableName', value: '{"a": "b"}'};

      // when

      await triggerVariableChange({
        node,
        ...variable
      });

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalledTimes(1);
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({variable});
    });

    it('should call onFilterChange with empty object (on invalid JSON value)', async () => {
      // given
      const variable = {name: 'variableName', value: '{{{{'};

      // when
      await act(async () => {
        await triggerVariableChange({
          node,
          ...variable
        });
      });

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalledTimes(1);
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({});
    });

    it('should call onFilterChange with empty object (on empty name)', async () => {
      // given
      const variable = {name: '', value: '{"a": "b"}'};

      // when
      await act(async () => {
        await triggerVariableChange({
          node,
          ...variable
        });
      });

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalledTimes(1);
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({});
    });

    it('should call onFilterChange with empty object (on empty value)', async () => {
      // given
      const variable = {name: 'myVariable', value: ''};

      // when
      await act(async () => {
        await triggerVariableChange({
          node,
          ...variable
        });
      });

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalledTimes(1);
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({});
    });

    it('should call onFilterChange with empty object (on empty name and value)', async () => {
      // given
      const variable = {name: '', value: ''};

      // when
      await triggerVariableChange({
        node,
        ...variable
      });

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalledTimes(1);
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({});
    });
  });

  describe('reset button', () => {
    it('should render the reset filters button', () => {
      // given filter is different from DEFAULT_FILTER_CONTROLLED_VALUES
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockProps}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const ResetButtonNode = node.find(Button);
      const onClick = ResetButtonNode.props().onClick;

      //when
      onClick();

      // then
      expect(ResetButtonNode.text()).toBe('Reset Filters');
      expect(ResetButtonNode).toHaveLength(1);
      expect(ResetButtonNode.prop('disabled')).toBe(false);
      expect(mockProps.onFilterReset).toHaveBeenCalled();
    });

    it('should render the disabled reset filters button', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockPropsWithDefaultFilter}
              filter={{
                ...DEFAULT_FILTER_CONTROLLED_VALUES,
                ...DEFAULT_FILTER
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

    it('should render the reset filters button after changing input', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockProps}
              filter={{...DEFAULT_FILTER_CONTROLLED_VALUES, ...DEFAULT_FILTER}}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const errorMessageInput = node.find('input[name="errorMessage"]');

      // when
      errorMessageInput.simulate('change', {
        target: {value: 'abc', name: 'errorMessage'}
      });

      const ResetButtonNode = node.find(Button);
      ResetButtonNode.simulate('click');

      // then
      expect(mockProps.onFilterReset).toHaveBeenCalled();
    });

    it('should reset all fields', async () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockProps}
              filter={COMPLETE_FILTER}
            />
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

    it('should call this.props.onFilterReset', async () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockProps}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const ResetButtonNode = node.find(Button);

      //when
      ResetButtonNode.simulate('click');
      node.update();

      // then
      expect(mockProps.onFilterReset).toHaveBeenCalled();
    });
  });

  describe('batchOperationId filter', () => {
    it('should render a batchOperationId field', async () => {
      jest.useFakeTimers();

      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere(n => n.props().name === 'batchOperationId')
        .find('input');

      field.simulate('change', {
        target: {value: 'asd', name: 'batchOperationId'}
      });

      jest.advanceTimersByTime(DEBOUNCE_DELAY);

      await flushPromises();

      expect(field.length).toEqual(1);
      expect(field.prop('placeholder')).toEqual('Operation Id');
      expect(field.prop('value')).toEqual('');
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({
        batchOperationId: 'asd'
      });
    });

    it('should not call onFilterChange before debounce delay', async () => {
      // given
      jest.useFakeTimers();

      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockProps}
              filter={DEFAULT_FILTER_CONTROLLED_VALUES}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere(n => n.props().name === 'batchOperationId')
        .find('input');

      field.simulate('change', {
        target: {value: 'asd', name: 'batchOperationId'}
      });

      await flushPromises();

      expect(mockProps.onFilterChange).not.toHaveBeenCalled();
    });

    // test behaviour here
    it('should initialize the field with empty value', () => {
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      expect(node.state().filter.batchOperationId).toEqual('');
    });

    it('should be prefilled with the value from props.filter.batchOperationId ', async () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters.WrappedComponent
              groupedWorkflows={workflows}
              {...mockPropsWithInitFilter}
              filter={COMPLETE_FILTER}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.ValidationTextInput)
        .filterWhere(n => n.props().name === 'batchOperationId');

      // then
      expect(field.props().value).toEqual('batch-operation-id-example');
    });

    it('should update state when input receives text', () => {
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      node.instance().handleControlledInputChange({
        target: {value: 'batch operation id', name: 'batchOperationId'}
      });

      expect(node.state().filter.batchOperationId).toEqual(
        'batch operation id'
      );
    });

    it('should call onFilterChange with the right batch operation id', async () => {
      // given
      jest.useFakeTimers();
      const batchOperationId = 'lorem ipsum';
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );
      const instance = node.instance();

      //when
      instance.handleControlledInputChange({
        target: {value: batchOperationId, name: 'batchOperationId'}
      });
      instance.waitForTimer(instance.propagateFilter);

      jest.advanceTimersByTime(DEBOUNCE_DELAY);

      await flushPromises();

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({
        batchOperationId
      });
    });

    it('should call onFilterChange with empty object', async () => {
      // given
      jest.useFakeTimers();
      const emptyBatchOperationId = '';
      const node = shallow(
        <Filters.WrappedComponent
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );
      const instance = node.instance();

      //when
      instance.handleControlledInputChange({
        target: {value: emptyBatchOperationId, name: 'batchOperationId'}
      });
      instance.waitForTimer(instance.propagateFilter);
      jest.advanceTimersByTime(DEBOUNCE_DELAY);

      await flushPromises();

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({});
    });
  });
});
