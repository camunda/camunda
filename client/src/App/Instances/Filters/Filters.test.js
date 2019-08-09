/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow, mount} from 'enzyme';

import {
  FILTER_TYPES,
  DEFAULT_FILTER,
  DEFAULT_FILTER_CONTROLLED_VALUES
} from 'modules/constants';
import Button from 'modules/components/Button';
import {mockResolvedAsyncFn} from 'modules/testUtils';
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
  COMPLETE_FILTER
} from './Filters.setup';

import {DEBOUNCE_DELAY, ALL_VERSIONS_OPTION} from './constants';

jest.mock('./constants');

api.fetchGroupedWorkflows = mockResolvedAsyncFn(groupedWorkflowsMock);

describe('Filters', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should render with the right initial state', () => {
    // given
    const node = shallow(
      <Filters
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
          <Filters
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
            <Filters
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
          ...DEFAULT_FILTER_CONTROLLED_VALUES,
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
            <Filters
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
        <Filters
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
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
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
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      node.instance().handleInputChange({
        target: {value: 'error message', name: 'errorMessage'}
      });

      expect(node.state().filter.errorMessage).toEqual('error message');
    });

    it('should call onFilterChange with the right error message', done => {
      // given
      const errorMessage = 'lorem ipsum';
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      node.instance().handleInputChange({
        target: {value: errorMessage, name: 'errorMessage'}
      });
      node.instance().handleFilterChangeDebounced();

      setTimeout(() => {
        // then
        expect(mockProps.onFilterChange).toHaveBeenCalledWith({
          ...DEFAULT_FILTER_CONTROLLED_VALUES,
          errorMessage
        });
        done();
      }, DEBOUNCE_DELAY * 2);
    });

    it('should call onFilterChange with empty error message', done => {
      // given
      const emptyErrorMessage = '';
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      node.instance().handleInputChange({
        target: {value: emptyErrorMessage, name: 'errorMessage'}
      });
      node.instance().handleFilterChangeDebounced();

      setTimeout(() => {
        // then
        expect(mockProps.onFilterChange).toHaveBeenCalledWith({
          ...DEFAULT_FILTER_CONTROLLED_VALUES,
          errorMessage: ''
        });
        done();
      }, DEBOUNCE_DELAY * 2);
    });
  });

  describe('ids filter', () => {
    it('should render an ids field', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
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
      const onBlur = field.props().onBlur;

      // when
      onBlur({target: {value: '', name: 'ids'}});

      // then
      expect(field).toExist();
      expect(field.prop('value')).toEqual('');
      expect(field.prop('placeholder')).toEqual(
        'Instance Id(s) separated by space or comma'
      );
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        ids: ''
      });
    });

    it('should initialize the field with empty value', () => {
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      expect(node.state().filter.ids).toEqual('');
    });

    it('should update state when input receives text', () => {
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      node.instance().handleInputChange({
        target: {value: 'aa, ab, ac', name: 'ids'}
      });

      expect(node.state().filter.ids).toEqual('aa, ab, ac');
    });

    it('should be prefilled with the value from props.filter.ids ', async () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
              filter={{...DEFAULT_FILTER_CONTROLLED_VALUES, ids: 'a, b, c'}}
            />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );

      const field = node
        .find(Styled.Textarea)
        .filterWhere(n => n.props().name === 'ids');

      // then
      expect(field.props().value).toEqual('a, b, c');
    });

    it('should call onFilterChange with the right instance ids', () => {
      const instanceIds = '4294968008,4294972032  4294974064, 4294976280, ,';
      // given
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      const instance = node.instance();
      instance.handleInputChange({target: {value: instanceIds, name: 'ids'}});
      instance.handleFilterChange();

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        ids: instanceIds
      });
    });

    it('should call onFilterChange with an empty array', () => {
      // given
      // user blurs without writing
      const emptyInstanceIds = '';
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      const instance = node.instance();
      instance.handleInputChange({
        target: {value: emptyInstanceIds, name: 'ids'}
      });
      instance.handleFilterChange();

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        ids: ''
      });
    });
  });

  describe('workflow filter', () => {
    it('should render an workflow select field', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
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
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        workflow: '',
        activityId: '',
        version: ''
      });
    });

    it('should render the value from this.props.filter.workflow', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
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

      // then
      expect(field.props().value).toEqual('demoProcess');
    });

    it('should have values read from this.props.groupedWorkflows', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
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
        <Filters
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
            <Filters
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
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
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
            <Filters
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
            <Filters
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
            <Filters
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
            <Filters
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
        <Filters
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
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        workflow: 'demoProcess',
        version: '3',
        activityId: ''
      });
    });

    it('should call onFilterChange when all workflow versions are selected', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      // select workflowName, the version is set to the latest
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();
      node
        .instance()
        .handleWorkflowVersionChange({target: {value: ALL_VERSIONS_OPTION}});

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalled();
      expect(mockProps.onFilterChange.mock.calls[0][0].version).toEqual('3');
      expect(mockProps.onFilterChange.mock.calls[0][0].activityId).toBe('');
      expect(mockProps.onFilterChange.mock.calls[1][0].version).toEqual('all');
      expect(mockProps.onFilterChange.mock.calls[1][0].activityId).toBe('');
    });
  });

  describe('selectable FlowNode filter', () => {
    it('should exist and be disabled by default', () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
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
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        activityId: ''
      });
    });

    it('should render the value from this.props.filter.activityId', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
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
            <Filters
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
            <Filters
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
            <Filters
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
            <Filters
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
            <Filters
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
            <Filters
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
        <Filters
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

      instance.handleInputChange({
        target: {
          value: mockPropsWithSelectableFlowNodes.selectableFlowNodes[0].id,
          name: 'activityId'
        }
      });
      instance.handleFilterChange();

      // then
      expect(node.state().filter.activityId).toEqual(activityId);
    });
  });

  describe('startDate filter', () => {
    it('should exist', done => {
      // given
      const target = {value: '1084-10-08', name: 'startDate'};
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
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

      setTimeout(() => {
        // then
        expect(field.length).toEqual(1);
        expect(field.props().placeholder).toEqual(
          'Start Date yyyy-mm-dd hh:mm:ss'
        );
        expect(field.props().value).toEqual('');
        expect(mockProps.onFilterChange).toHaveBeenCalledWith({
          ...DEFAULT_FILTER_CONTROLLED_VALUES,
          startDate: '1084-10-08'
        });
        done();
      }, DEBOUNCE_DELAY);
    });

    it('should be prefilled with the value from props.filter.startDate', async () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
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
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      node.instance().handleInputChange({
        target: {value: '2009-01-25 10:23:01', name: 'startDate'}
      });
      node.update();

      expect(node.state().filter.startDate).toEqual('2009-01-25 10:23:01');
    });

    it('should update the filters in Instances page', async () => {
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      const instance = node.instance();
      instance.handleInputChange({
        target: {value: '2009-01-25', name: 'startDate'}
      });
      instance.handleFilterChange();
      node.update();

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalled();
      expect(mockProps.onFilterChange.mock.calls[0][0].startDate).toBe(
        '2009-01-25'
      );
    });

    it('should send null values for empty start dates', async () => {
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      const instance = node.instance();
      instance.handleInputChange({
        target: {value: '', name: 'startDate'}
      });
      instance.handleFilterChange();
      node.update();

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalledWith({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        startDate: ''
      });
    });
  });

  describe('endDate filter', () => {
    it('should exist', done => {
      // given
      const target = {value: '1984-10-08', name: 'endDate'};
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
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

      // TODO(paddy): maybe it's possible to use act() instead of setTimeout from React 16.9
      setTimeout(() => {
        // then
        expect(field.length).toEqual(1);
        expect(field.props().name).toEqual('endDate');
        expect(field.props().placeholder).toEqual(
          'End Date yyyy-mm-dd hh:mm:ss'
        );
        expect(field.props().value).toEqual('');
        expect(mockProps.onFilterChange).toHaveBeenCalledWith({
          ...DEFAULT_FILTER_CONTROLLED_VALUES,
          endDate: '1984-10-08'
        });
        done();
      }, DEBOUNCE_DELAY * 2);
    });

    it('should be prefilled with the value from props.filter.endDate', async () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
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
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      node.instance().handleInputChange({
        target: {value: '2009-01-25', name: 'endDate'}
      });
      node.update();

      // then
      expect(node.state().filter.endDate).toEqual('2009-01-25');
    });

    it('should update the filters in Instances page', async () => {
      const node = shallow(
        <Filters
          groupedWorkflows={workflows}
          {...mockProps}
          filter={DEFAULT_FILTER_CONTROLLED_VALUES}
        />
      );

      //when
      const instance = node.instance();
      instance.handleInputChange({
        target: {value: '2009-01-25', name: 'endDate'}
      });
      instance.handleFilterChange();
      node.update();

      // then
      expect(mockProps.onFilterChange).toHaveBeenCalled();
      expect(mockProps.onFilterChange.mock.calls[0][0].endDate).toBe(
        '2009-01-25'
      );
    });
  });

  describe('reset button', () => {
    it('should render the reset filters button', () => {
      // given filter is different from DEFAULT_FILTER_CONTROLLED_VALUES
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
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
            <Filters
              groupedWorkflows={workflows}
              {...mockProps}
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
            <Filters
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
            <Filters
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
    });

    it('should call this.props.onFilterReset', async () => {
      // given
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <Filters
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
});
