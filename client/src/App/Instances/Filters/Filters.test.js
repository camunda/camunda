import React from 'react';
import {shallow} from 'enzyme';

import {DEFAULT_FILTER, FILTER_TYPES, DIRECTION} from 'modules/constants';
import Button from 'modules/components/Button';
import Textarea from 'modules/components/Textarea';
import TextInput from 'modules/components/TextInput';
import Select from 'modules/components/Select';
import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';
import * as api from 'modules/api/instances/instances';

import Filters from './Filters';
import CheckboxGroup from './CheckboxGroup/';
import * as Styled from './styled';
import {parseWorkflowNames} from './service';
import {ALL_VERSIONS_OPTION, EMPTY_OPTION} from './constants';

const COMPLETE_FILTER = {
  ...DEFAULT_FILTER,
  ids: ['a', 'b', 'c'],
  errorMessage: 'This is an error message'
};

const groupedWorkflowsMock = [
  {
    bpmnProcessId: 'demoProcess',
    name: 'New demo process',
    workflows: [
      {
        id: '6',
        name: 'New demo process',
        version: 3,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '4',
        name: 'Demo process',
        version: 2,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '1',
        name: 'Demo process',
        version: 1,
        bpmnProcessId: 'demoProcess'
      }
    ]
  },
  {
    bpmnProcessId: 'orderProcess',
    name: 'Order',
    workflows: []
  }
];

api.fetchGroupedWorkflowInstances = mockResolvedAsyncFn(groupedWorkflowsMock);

describe('Filters', () => {
  const spy = jest.fn();
  const instancesSpy = jest.fn();
  const mockProps = {
    onFilterChange: spy,
    resetFilter: jest.fn(),
    onWorkflowVersionChange: instancesSpy,
    activityIds: []
  };

  const mockPropsWithActivityIds = {
    onFilterChange: spy,
    resetFilter: jest.fn(),
    onWorkflowVersionChange: instancesSpy,
    activityIds: [
      {value: 'taskA', label: 'task A'},
      {value: 'taskB', label: 'taskB'}
    ]
  };

  beforeEach(() => {
    spy.mockClear();
    instancesSpy.mockClear();
  });

  it('should render with the right initial state', () => {
    // given
    const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

    // then
    expect(node.state().groupedWorkflows).toEqual([]);
    expect(node.state().currentWorkflow).toEqual({});
    expect(node.state().currentWorkflowVersion).toEqual(EMPTY_OPTION);
    expect(node.state().filter.activityId).toEqual(EMPTY_OPTION);
    expect(node.state().filter.ids).toEqual('');
    expect(node.state().filter.errorMessage).toEqual('');
    expect(node.state().workflowNameOptions).toEqual([]);
  });

  it('should render the running and finished filters', () => {
    // given
    const {active, incidents, completed, canceled} = DEFAULT_FILTER;

    const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);
    const FilterNodes = node.find(CheckboxGroup);

    // then
    expect(FilterNodes).toHaveLength(2);
    expect(FilterNodes.at(0).prop('type')).toBe(FILTER_TYPES.RUNNING);
    expect(FilterNodes.at(0).prop('filter')).toEqual({active, incidents});
    expect(FilterNodes.at(0).prop('onChange')).toBe(mockProps.onFilterChange);
    expect(FilterNodes.at(1).prop('type')).toBe(FILTER_TYPES.FINISHED);
    expect(FilterNodes.at(1).prop('filter')).toEqual({completed, canceled});
    expect(FilterNodes.at(1).prop('onChange')).toBe(mockProps.onFilterChange);
  });

  it('should render the expand button with left direction', () => {
    // given
    const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);
    const ExpandButtonNode = node.find(Styled.ExpandButton);

    // then
    expect(ExpandButtonNode).toHaveLength(1);
    expect(ExpandButtonNode.prop('direction')).toBe(DIRECTION.LEFT);
    expect(ExpandButtonNode.prop('isExpanded')).toBe(true);
  });

  it('should render the disabled reset filters button', () => {
    // given filter is different from DEFAULT_FILTER
    const node = shallow(<Filters {...mockProps} filter={COMPLETE_FILTER} />);
    const ResetButtonNode = node.find(Button);

    // then
    expect(ResetButtonNode).toHaveLength(1);
    expect(ResetButtonNode.prop('disabled')).toBe(false);
    expect(ResetButtonNode.prop('onClick')).toBe(mockProps.resetFilter);
  });

  it('should render the disabled reset filters button', () => {
    // given
    const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);
    const ResetButtonNode = node.find(Button);

    // then
    expect(ResetButtonNode).toHaveLength(1);
    expect(ResetButtonNode.prop('disabled')).toBe(true);
  });

  describe('errorMessage filter', () => {
    it('should render an errorMessage field', () => {
      // given
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);
      const field = node.find({name: 'errorMessage'});

      // then
      expect(field.length).toEqual(1);
      expect(field.type()).toEqual(TextInput);
      expect(field.props().name).toEqual('errorMessage');
      expect(field.props().onBlur).toEqual(node.instance().handleFieldChange);
      expect(field.props().onChange).toEqual(node.instance().onFieldChange);
    });

    it('should initialize the field with empty value', () => {
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      expect(node.state().filter.errorMessage).toEqual('');
    });

    it('should be prefilled with the value from props.filter.errorMessage ', async () => {
      const node = shallow(<Filters {...mockProps} filter={COMPLETE_FILTER} />);

      //when
      await flushPromises();
      node.update();
      const field = node.find({name: 'errorMessage'});
      // then
      expect(node.state().filter.errorMessage).toEqual(
        'This is an error message'
      );
      expect(field.props().value).toEqual('This is an error message');
    });

    it('should update state when input receives text', () => {
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      node.instance().onFieldChange({
        target: {value: 'error message', name: 'errorMessage'}
      });

      expect(node.state().filter.errorMessage).toEqual('error message');
    });

    it('should call onFilterChange with the right error message', () => {
      const errorMessage = 'lorem ipsum';
      // given
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      node.instance().handleFieldChange({
        target: {value: errorMessage, name: 'errorMessage'}
      });

      // then
      expect(spy).toHaveBeenCalledWith({errorMessage});
    });

    it('should call onFilterChange with empty error message', () => {
      // given
      // user blurs without writing
      const emptyErrorMessage = '';
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      node.instance().handleFieldChange({
        target: {value: emptyErrorMessage, name: 'errorMessage'}
      });

      // then
      expect(spy).toHaveBeenCalledWith({errorMessage: null});
    });
  });

  describe('instanceIds filter', () => {
    it('should render an instanceIds field', () => {
      // given
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);
      const field = node.find({name: 'ids'});

      // then
      expect(field.length).toEqual(1);
      expect(field.type()).toEqual(Textarea);
      expect(field.props().name).toEqual('ids');
      expect(field.props().onBlur).toEqual(node.instance().handleFieldChange);
      expect(field.props().onChange).toEqual(node.instance().onFieldChange);
    });

    it('should initialize the field with empty value', () => {
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      expect(node.state().filter.ids).toEqual('');
    });

    it('should update state when input receives text', () => {
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      node.instance().onFieldChange({
        target: {value: "['a', 'b', 'c']", name: 'ids'}
      });

      expect(node.state().filter.ids).toEqual("['a', 'b', 'c']");
    });

    it('should be prefilled with the value from props.filter.ids ', async () => {
      const node = shallow(<Filters {...mockProps} filter={COMPLETE_FILTER} />);

      //when
      await flushPromises();
      node.update();
      const field = node.find({name: 'ids'});
      // then
      expect(node.state().filter.ids).toEqual(['a', 'b', 'c']);
      expect(field.props().value).toEqual(['a', 'b', 'c']);
    });

    it('should call onFilterChange with the right instance ids', () => {
      const instanceIds = '4294968008,4294972032  4294974064, 4294976280, ,';
      // given
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      node
        .instance()
        .handleFieldChange({target: {value: instanceIds, name: 'ids'}});

      // then
      expect(spy).toHaveBeenCalledWith({
        ids: ['4294968008', '4294972032', '4294974064', '4294976280']
      });
    });

    it('should call onFilterChange with an empty array', () => {
      // given
      // user blurs without writing
      const emptyInstanceIds = '';
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      node
        .instance()
        .handleFieldChange({target: {value: emptyInstanceIds, name: 'ids'}});

      // then
      expect(spy).toHaveBeenCalledWith({
        ids: []
      });
    });
  });

  describe('workflowName filter', () => {
    it('should render an workflowName select field', () => {
      // given
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);
      const field = node.find({name: 'workflowName'});

      // then
      expect(field.length).toEqual(1);
      expect(field.type()).toEqual(Select);
      expect(field.props().name).toEqual('workflowName');
      expect(field.props().value).toEqual('');
      expect(field.props().placeholder).toEqual('Workflow');
      expect(field.props().onChange).toEqual(
        node.instance().handleWorkflowNameChange
      );
    });

    it('should fetch grouped workflows and set state', async () => {
      // given
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      await flushPromises();
      node.update();

      // then
      expect(api.fetchGroupedWorkflowInstances).toHaveBeenCalled();
      expect(node.state().groupedWorkflows).toEqual(groupedWorkflowsMock);
      expect(node.state().workflowNameOptions).toEqual(
        parseWorkflowNames(groupedWorkflowsMock)
      );
      expect(node.find({name: 'workflowName'}).props().options).toEqual(
        node.state().workflowNameOptions
      );
    });

    it('should update state with selected option', async () => {
      // given
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      await flushPromises();
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      // then
      expect(node.state().currentWorkflow).toEqual(groupedWorkflowsMock[0]);
    });
  });

  describe('workflowVersion filter', () => {
    it('should exist and be disabled by default', () => {
      // given
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);
      const field = node.find({name: 'workflowIds'});

      // then
      expect(field.length).toEqual(1);
      expect(field.type()).toEqual(Select);
      expect(field.props().name).toEqual('workflowIds');
      expect(field.props().value).toEqual('');
      expect(field.props().placeholder).toEqual('Workflow Version');
      expect(field.props().onChange).toEqual(
        node.instance().handleWorkflowVersionChange
      );
    });

    it('should display the latest version of a selected workflowName', async () => {
      // given
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      await flushPromises();
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      // then
      expect(node.find({name: 'workflowIds'}).props().value).toEqual(
        groupedWorkflowsMock[0].workflows[0].id
      );
      expect(instancesSpy.mock.calls[0][0]).toEqual(
        groupedWorkflowsMock[0].workflows[0]
      );
    });

    it('should display an all versions option', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      await flushPromises();
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      const options = node.find({name: 'workflowIds'}).props().options;

      // then
      expect(options[0].label).toEqual('Version 3');
      expect(options[options.length - 1].value).toEqual(ALL_VERSIONS_OPTION);
      expect(options[options.length - 1].label).toEqual('All versions');
      // groupedWorkflowsMock.workflows.length + 1 (All versions)
      expect(options.length).toEqual(4);
    });

    it('should not allow the selection of the first option', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      await flushPromises();
      // select workflowName, the version is set to the latest
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      // select WorkflowVersion option, 1st
      node.instance().handleWorkflowVersionChange({target: {value: ''}});
      node.update();

      // then
      // should keep the last version option selected
      expect(node.find({name: 'workflowIds'}).props().value).toEqual(
        groupedWorkflowsMock[0].workflows[0].id
      );
      // should update the workflow in Instances
      expect(instancesSpy.mock.calls.length).toEqual(1);
    });

    it('should reset after a the workflowName field is also reseted ', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      await flushPromises();
      // select workflowName, the version is set to the latest
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      // select WorkflowVersion option, 1st
      node.instance().handleWorkflowNameChange({target: {value: ''}});
      node.update();

      // then
      // should keep the last version option selected
      expect(node.find({name: 'workflowIds'}).props().value).toEqual('');
      expect(node.find({name: 'workflowName'}).props().value).toEqual('');
      // should update the instances diagrams
      expect(instancesSpy.mock.calls[1][0]).toEqual(null);
    });

    it('should call onFilterChange when a workflow version is selected', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      await flushPromises();
      // select workflowName, the version is set to the latest
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      // then
      expect(spy).toHaveBeenCalledWith({workflowIds: ['6']});
    });

    it('should call onFilterChange when all workflow versions are selected', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      await flushPromises();
      // select workflowName, the version is set to the latest
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();
      node
        .instance()
        .handleWorkflowVersionChange({target: {value: ALL_VERSIONS_OPTION}});

      // then
      expect(spy).toHaveBeenCalledWith({workflowIds: ['6', '4', '1']});
    });
  });

  describe('activityId filter', () => {
    it('should exist and be disabled by default', () => {
      // given
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);
      const field = node.find({name: 'activityId'});

      // then
      expect(field.length).toEqual(1);
      expect(field.type()).toEqual(Select);
      expect(field.props().name).toEqual('activityId');
      expect(field.props().value).toEqual('');
      expect(field.props().placeholder).toEqual('Flow Node');
      expect(field.props().onChange).toEqual(
        node.instance().handleActivityIdChange
      );
      expect(field.props().disabled).toBe(true);
      expect(field.props().options.length).toBe(0);
    });

    it('should be disabled if All versions is selected', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      await flushPromises();
      // select workflowName, the version is set to the latest
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      node
        .instance()
        .handleWorkflowVersionChange({target: {value: ALL_VERSIONS_OPTION}});
      node.update();

      // then
      expect(node.find({name: 'activityId'}).props().disabled).toEqual(true);
    });

    it('should not be disabled when a version is selected', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      await flushPromises();
      // select workflowName, the version is set to the latest
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      node.instance().handleWorkflowVersionChange({
        target: {value: groupedWorkflowsMock[0].workflows[0].id}
      });
      node.update();

      // then
      expect(node.find({name: 'activityId'}).props().disabled).toEqual(false);
      expect(node.find({name: 'activityId'}).props().value).toEqual('');
    });

    it('should be disabled after the workflow name is reseted', async () => {
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      await flushPromises();
      // select workflowName, the version is set to the latest
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      node.instance().handleWorkflowNameChange({target: {value: ''}});
      node.update();

      // then
      expect(node.find({name: 'activityId'}).props().disabled).toEqual(true);
      expect(node.find({name: 'activityId'}).props().options.length).toEqual(0);
    });

    it('should display a list of activity ids', async () => {
      // given
      const node = shallow(
        <Filters {...mockPropsWithActivityIds} filter={DEFAULT_FILTER} />
      );

      // then
      expect(node.find({name: 'activityId'}).props().options.length).toEqual(2);
    });

    it('should set the state on activityId selection', async () => {
      // given
      const value = groupedWorkflowsMock[0].bpmnProcessId;
      const activityId = mockPropsWithActivityIds.activityIds[0].value;
      const node = shallow(
        <Filters {...mockPropsWithActivityIds} filter={DEFAULT_FILTER} />
      );

      //when
      await flushPromises();
      // select workflowName, the version is set to the latest
      node.instance().handleWorkflowNameChange({target: {value: value}});
      node.update();

      node.instance().handleActivityIdChange({
        target: {
          value: mockPropsWithActivityIds.activityIds[0].value,
          name: 'activityId'
        },

        persist: () => {}
      });

      // then
      expect(node.state().filter.activityId).toEqual(activityId);
    });
  });

  describe('startDate filter', () => {
    it('should exist', () => {
      // given
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);
      const field = node.find({name: 'startDate'});

      // then
      expect(field.length).toEqual(1);
      expect(field.type()).toEqual(TextInput);
      expect(field.props().name).toEqual('startDate');
      expect(field.props().placeholder).toEqual('Start Date');
      expect(field.props().onBlur).toEqual(node.instance().handleFieldChange);
    });

    it('should update the filters with startDateAfter and startDateBefore values', async () => {
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      await flushPromises();

      node.instance().handleFieldChange({
        target: {value: '25 January 2009', name: 'startDate'}
      });
      node.update();

      // then
      expect(spy).toHaveBeenCalled();
      expect(spy.mock.calls[0][0].startDateAfter).toContain(
        '2009-01-25T00:00:00.000'
      );
      expect(spy.mock.calls[0][0].startDateBefore).toContain(
        '2009-01-26T00:00:00.000'
      );
    });

    it('should send null values for empty start dates', async () => {
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      await flushPromises();

      node.instance().handleFieldChange({
        target: {value: '', name: 'startDate'}
      });
      node.update();

      // then
      expect(spy).toHaveBeenCalledWith({
        startDateAfter: null,
        startDateBefore: null
      });
    });

    it('should not update the filters when startDate is invalid', async () => {
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      await flushPromises();

      node.instance().handleFieldChange({
        target: {value: 'invalid date', name: 'startDate'}
      });
      node.update();

      // then
      expect(spy).toHaveBeenCalledWith({});
    });
  });

  describe('endDate filter', () => {
    it('should exist', () => {
      // given
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);
      const field = node.find({name: 'endDate'});

      // then
      expect(field.length).toEqual(1);
      expect(field.type()).toEqual(TextInput);
      expect(field.props().name).toEqual('endDate');
      expect(field.props().placeholder).toEqual('End Date');
      expect(field.props().onBlur).toEqual(node.instance().handleFieldChange);
    });

    it('should update the filters with endDateAfter and endDateBefore values', async () => {
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      await flushPromises();

      node.instance().handleFieldChange({
        target: {value: '25 January 2009', name: 'endDate'}
      });
      node.update();

      // then
      expect(spy).toHaveBeenCalled();
      expect(spy.mock.calls[0][0].endDateAfter).toContain(
        '2009-01-25T00:00:00.000'
      );
      expect(spy.mock.calls[0][0].endDateBefore).toContain(
        '2009-01-26T00:00:00.000'
      );
    });

    it('should send null values for empty or invalid endDate', async () => {
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      await flushPromises();

      node.instance().handleFieldChange({
        target: {value: 'invalid date', name: 'endDate'}
      });
      node.update();

      // then
      expect(spy).toHaveBeenCalledWith({});
    });

    it('should not update the filters when endDate is invalid', async () => {
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      await flushPromises();

      node.instance().handleFieldChange({
        target: {value: '', name: 'endDate'}
      });
      node.update();

      // then
      expect(spy).toHaveBeenCalledWith({
        endDateAfter: null,
        endDateBefore: null
      });
    });
  });
});
