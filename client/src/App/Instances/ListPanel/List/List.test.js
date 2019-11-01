/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {EXPAND_STATE, SORT_ORDER} from 'modules/constants';

import Table from 'modules/components/Table';
import Actions from 'modules/components/Actions';

import StateIcon from 'modules/components/StateIcon';
import Checkbox from 'modules/components/Checkbox';
import {getWorkflowName} from 'modules/utils/instance';
import {formatDate} from 'modules/utils/date';

import {xTimes, createInstance} from 'modules/testUtils';

import List from './List';

import ColumnHeader from './ColumnHeader';
import * as Styled from './styled';
import * as ListContext from './ListContext';

const {THead, TBody, TH, TR, TD} = Table;

jest.mock('modules/utils/bpmn');
// Create Mock Data
let instances = [];

jest.mock('./ListContext');

const createMockInstances = (amount, array) =>
  xTimes(amount)(index =>
    array.push(createInstance({id: index.toString(), state: 'ACTIVE'}))
  );

createMockInstances(1, instances);

const mockProps = {
  data: instances,
  onSelectedInstancesUpdate: jest.fn(),
  onEntriesPerPageChange: jest.fn(),
  filter: {active: true, incidents: true},
  onSort: jest.fn(),
  selectedInstances: {
    all: false,
    excludeIds: [],
    ids: [0, 10]
  },
  expandState: EXPAND_STATE.DEFAULT,
  sorting: {sortBy: 'foo', sortOrder: SORT_ORDER.ASC},
  onActionButtonClick: jest.fn()
};

ListContext.useListContext.mockImplementation(() => {
  return {
    data: instances,
    rowsToDisplay: 9,
    sorting: mockProps.sorting,
    filter: mockProps.filter,
    onSort: mockProps.onSort,
    areAllInstancesSelected: () => false,
    isSelected: () => true,
    handleSelectAll: () => {},
    handleSelectInstance: () => jest.fn(),
    handleActionButtonClick: mockProps.onActionButtonClick
  };
});

describe('List', () => {
  describe('Table', () => {
    it('should have by default rowsToDisplay 9', () => {
      const node = shallow(<List.WrappedComponent {...mockProps} />);
      expect(node.instance().state.rowsToDisplay).toBe(9);
    });

    it('should render table container with innerRef', () => {
      // given
      const node = shallow(<List.WrappedComponent {...mockProps} />);

      const containerNode = node.find(Styled.TableContainer);

      expect(containerNode).toExist();
    });

    describe('recalculateHeight', () => {
      it('should be called on mount', () => {
        // given
        const recalculateHeightSpy = jest.spyOn(
          List.WrappedComponent.prototype,
          'recalculateHeight'
        );
        shallow(<List.WrappedComponent {...mockProps} />);

        // then
        expect(recalculateHeightSpy).toBeCalled();
      });

      it('should only be called when needed', () => {
        // given
        const recalculateHeightSpy = jest.spyOn(
          List.WrappedComponent.prototype,
          'recalculateHeight'
        );
        const node = shallow(<List.WrappedComponent {...mockProps} />);
        recalculateHeightSpy.mockClear();

        // when component updates but expandState does not change
        node.setProps({expandState: EXPAND_STATE.DEFAULT});
        // then recalculateHeight should not be called
        expect(recalculateHeightSpy).not.toBeCalled();

        // when component updates but expandState is COLLAPSED
        node.setProps({expandState: EXPAND_STATE.COLLAPSED});
        // then recalculateHeight should not be called
        expect(recalculateHeightSpy).not.toBeCalled();

        // when component updates and expandState changed and is not COLLAPSED
        node.setProps({expandState: EXPAND_STATE.EXPANDED});
        // then recalculateHeight should not be called
        expect(recalculateHeightSpy).toBeCalled();
      });

      it('should set state.rowsToDisplay', () => {
        // given
        const node = shallow(<List.WrappedComponent {...mockProps} />);
        node.instance().myRef.current = {clientHeight: 38};
        const expectedRows = 0;

        // when
        node.instance().recalculateHeight();
        node.update();

        // then
        expect(node.state().rowsToDisplay).toBe(expectedRows);
        expect(mockProps.onEntriesPerPageChange).toBeCalledWith(expectedRows);
      });
    });
  });

  describe('Selected Instances', () => {
    let node;
    let instances = [];
    createMockInstances(3, instances);

    beforeEach(() => {
      mockProps.data = instances;
      node = shallow(<List.WrappedComponent {...mockProps} />);
    });

    describe('highlight', () => {
      it('should highlight all instances when all are selected', () => {
        node.setProps({
          selectedInstances: {
            all: true,
            ids: [],
            excludeIds: []
          }
        });

        // then
        expect(node.instance().isSelected(10)).toBe(true);
        expect(node.instance().isSelected(9)).toBe(true);
        expect(node.instance().isSelected(8)).toBe(true);
      });

      it('should not highlight any instance if none is selected', () => {
        node.setProps({
          selectedInstances: {
            all: false,
            ids: [],
            excludeIds: []
          }
        });

        // then
        expect(node.instance().isSelected(10)).toBe(false);
        expect(node.instance().isSelected(9)).toBe(false);
        expect(node.instance().isSelected(8)).toBe(false);
      });

      it('should highlight an instance when it is selected', () => {
        // given
        const selectedInstanceId = 10;
        node.setProps({
          selectedInstances: {
            all: false,
            excludeIds: [],
            ids: [selectedInstanceId]
          }
        });
        expect(node.instance().isSelected(selectedInstanceId)).toBe(true);

        // when
        node.setProps({
          selectedInstances: {all: false, excludeIds: [], ids: []}
        });

        // then
        expect(node.instance().isSelected(selectedInstanceId)).toBe(false);
      });

      it('should not highlight an instance if excluded from selectAll ', () => {
        // when
        const selectedInstanceId = 10;
        node.setProps({
          selectedInstances: {
            all: true,
            ids: [],
            excludeIds: [selectedInstanceId]
          }
        });
        // then
        expect(node.instance().isSelected(selectedInstanceId)).toBe(false);
      });

      it('should set selectedInstances.all to true', () => {
        // given
        const onSelectedInstancesUpdate = jest.fn();

        node.setProps({
          filterCount: 2,
          selectedInstances: {
            all: false,
            ids: [10],
            excludeIds: []
          },
          onSelectedInstancesUpdate: onSelectedInstancesUpdate
        });

        // when
        const select = node.instance().handleSelectInstance({id: 5});
        select(undefined, true);

        // then
        expect(onSelectedInstancesUpdate).toBeCalledWith({
          all: true,
          ids: [],
          excludeIds: []
        });
      });

      it('should set selectedInstances.all to false', () => {
        // given
        const onSelectedInstancesUpdate = jest.fn();

        node.setProps({
          filterCount: 2,
          selectedInstances: {
            all: true,
            ids: [],
            excludeIds: [10]
          },
          onSelectedInstancesUpdate: onSelectedInstancesUpdate
        });

        // when
        const select = node.instance().handleSelectInstance({id: 5});
        select(undefined, false);
        node.update();

        // then
        expect(onSelectedInstancesUpdate).toBeCalledWith({
          all: false,
          ids: [],
          excludeIds: []
        });
      });

      it('should return that all instances are selected', () => {
        // when
        node.setProps({
          selectedInstances: {
            all: true,
            ids: [],
            excludeIds: []
          }
        });

        // then
        expect(node.instance().areAllInstancesSelected()).toBe(true);
      });

      it('should return that not all instances are selected when any instance is excluded', () => {
        // when
        node.setProps({
          selectedInstances: {
            all: true,
            ids: [],
            excludeIds: [10]
          }
        });

        // then
        expect(node.instance().areAllInstancesSelected()).toBe(false);
      });
    });
  });

  describe('Message', () => {
    let node;
    beforeEach(() => {
      const message = 'some message';
      node = shallow(
        <List.WrappedComponent.Message {...mockProps} {...{message}} />
      );
    });

    it('should be wrapped correctly', () => {
      expect(node).toMatchSnapshot();
    });
  });

  describe('Skeleton & Spinners', () => {
    it('Skeleton should be wrapped correctly', () => {
      const node = shallow(<List.WrappedComponent.Skeleton {...mockProps} />);
      expect(node).toMatchSnapshot();
    });
  });

  describe('Body', () => {
    it('should render semantic html table body', () => {
      const node = shallow(<List.WrappedComponent.Body {...mockProps} />);

      // then
      // TBody
      const TBodyNode = node.find(TBody);
      expect(TBodyNode).toHaveLength(1);

      // TR
      const TRNodes = TBodyNode.find(TR);
      expect(TRNodes.length).toBe(1);
      TRNodes.forEach((TRNode, idx) => {
        const currentInstance = mockProps.data[idx];

        // TD
        const TDNodes = TRNode.find(TD);
        expect(TDNodes.length).toBe(6);

        // Workflow Definition TD
        expect(TDNodes.at(0).find(Checkbox)).toHaveLength(1);
        expect(TDNodes.at(0).contains(getWorkflowName(currentInstance))).toBe(
          true
        );
        // State Icon
        const StateIconNode = TDNodes.at(0).find(StateIcon);
        expect(StateIconNode).toHaveLength(1);
        expect(StateIconNode.prop('state')).toEqual(currentInstance.state);

        // Instance Id TD Anchor
        const InstanceAnchorNode = TDNodes.at(1).find(Styled.InstanceAnchor);
        expect(InstanceAnchorNode).toHaveLength(1);
        expect(InstanceAnchorNode.prop('to')).toBe(`/instances/1`);
        expect(InstanceAnchorNode.contains('1')).toBe(true);

        // Version TD
        expect(
          TDNodes.at(2).contains(`Version ${currentInstance.workflowVersion}`)
        ).toBe(true);

        // Start Date TD
        expect(
          TDNodes.at(3).contains(formatDate(currentInstance.startDate))
        ).toBe(true);

        // End Date TD
        expect(
          TDNodes.at(4).contains(formatDate(currentInstance.endDate))
        ).toBe(true);

        // Actions TD
        expect(TDNodes.at(5).find(Actions));
      });
    });

    // context needs to be mocked fist.
    describe.only('Action button handler', () => {
      it('should pass the onActionButtonClick prop to Actions', () => {
        const node = shallow(<List.WrappedComponent.Body {...mockProps} />);
        const ActionsNode = node.find(Actions).at(0); // first row
        const onButtonClick = ActionsNode.prop('onButtonClick');

        // when an action button is clicked on first instance in list
        onButtonClick();

        // then expect handler to be called with the first instance
        expect(mockProps.onActionButtonClick).toHaveBeenCalledTimes(1);
        expect(mockProps.onActionButtonClick.mock.calls[0][0]).toEqual(
          mockProps.data[0]
        );
      });
    });
  });

  describe('Header', () => {
    const node = shallow(<List.WrappedComponent.Header {...mockProps} />);

    // then

    // THead
    const THeadNode = node.find(THead);
    expect(THeadNode).toHaveLength(1);

    // TH
    const THNodes = THeadNode.find(TH);
    expect(THNodes.length).toBe(5);

    // Actions TH
    const ActionsTHNode = THeadNode.find(Styled.ActionsTH);
    expect(ActionsTHNode.length).toBe(1);
    expect(ActionsTHNode.find(ColumnHeader).props().label).toBe('Actions');

    // Workflow Definition TH
    expect(
      THNodes.at(0)
        .find('ColumnHeader')
        .prop('label')
    ).toBe('Workflow');
    const CheckboxNode = THNodes.at(0).find(Checkbox);
    expect(CheckboxNode).toHaveLength(1);
    expect(CheckboxNode.prop('isChecked')).toBe(false);

    // Instance Id TH
    const InstanceIdTHNode = THNodes.at(1);

    // Instance Id ColumnHeader
    const InstanceColumnHeaderNode = InstanceIdTHNode.find(ColumnHeader);
    expect(InstanceColumnHeaderNode).toHaveLength(1);
    expect(InstanceColumnHeaderNode.prop('sortKey')).toBe('id');
    expect(InstanceColumnHeaderNode.prop('sorting')).toEqual(mockProps.sorting);
    expect(InstanceColumnHeaderNode.prop('onSort')).toBe(mockProps.onSort);
    expect(InstanceColumnHeaderNode.prop('label')).toBe('Instance Id');

    // Version TH
    const VersionTHNode = THNodes.at(2);

    // Version ColumnHeader
    const InstanceHeaderSortVersionNode = VersionTHNode.find(ColumnHeader);
    expect(InstanceHeaderSortVersionNode).toHaveLength(1);
    expect(InstanceHeaderSortVersionNode.prop('sortKey')).toBe(
      'workflowVersion'
    );
    expect(InstanceHeaderSortVersionNode.prop('sorting')).toEqual(
      mockProps.sorting
    );
    expect(InstanceHeaderSortVersionNode.prop('onSort')).toBe(mockProps.onSort);
    expect(InstanceHeaderSortVersionNode.prop('label')).toBe('Version');

    // Start Time TH
    const StartTimeTHNode = THNodes.at(3);

    // Start Time ColumnHeader
    const STimeColumnHeaderNode = StartTimeTHNode.find(ColumnHeader);
    expect(STimeColumnHeaderNode.prop('sortKey')).toBe('startDate');
    expect(STimeColumnHeaderNode.prop('sorting')).toEqual(mockProps.sorting);
    expect(STimeColumnHeaderNode.prop('onSort')).toBe(mockProps.onSort);
    expect(STimeColumnHeaderNode.prop('label')).toBe('Start Time');

    // End Time TH
    const EndTimeTHNode = THNodes.at(4);

    // Start Time ColumnHeader
    const ETimeColumnHeaderNode = EndTimeTHNode.find(ColumnHeader);
    expect(ETimeColumnHeaderNode.prop('sortKey')).toBe('endDate');
    expect(ETimeColumnHeaderNode.prop('sorting')).toEqual(mockProps.sorting);
    expect(ETimeColumnHeaderNode.prop('onSort')).toBe(mockProps.onSort);
    expect(ETimeColumnHeaderNode.prop('label')).toBe('End Time');
  });
});
