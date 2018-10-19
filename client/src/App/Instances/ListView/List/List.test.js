import React from 'react';
import {shallow} from 'enzyme';

import {EXPAND_STATE, SORT_ORDER} from 'modules/constants';
import Checkbox from 'modules/components/Checkbox';
import Table from 'modules/components/Table';
import StateIcon from 'modules/components/StateIcon';
import Actions from 'modules/components/Actions';
import {getWorkflowName} from 'modules/utils/instance';
import {formatDate} from 'modules/utils/date';

import {xTimes, createInstance} from 'modules/testUtils';

import List from './List';
import HeaderSortIcon from './HeaderSortIcon';
import * as Styled from './styled';

const {THead, TBody, TH, TR, TD} = Table;

// Create Mock Data
let instances = [];

const createMockInstances = (amount, array) =>
  xTimes(amount)(index =>
    array.push(createInstance({id: index.toString(), state: 'ACTIVE'}))
  );

createMockInstances(1, instances);

const mockProps = {
  data: instances,
  onUpdateSelection: jest.fn(),
  onEntriesPerPageChange: jest.fn(),
  filter: {active: true, incidents: true},
  handleSorting: jest.fn(),
  selection: {
    all: false,
    excludeIds: [],
    ids: [0, 10]
  },
  expandState: EXPAND_STATE.DEFAULT,
  sorting: {sortBy: 'foo', sortOrder: SORT_ORDER.ASC}
};

const emptyList = {
  data: [],
  onUpdateSelection: jest.fn(),
  onEntriesPerPageChange: jest.fn(),
  handleSorting: jest.fn(),
  selection: {
    all: false,
    excludeIds: [],
    ids: []
  },
  expandState: EXPAND_STATE.DEFAULT,
  sorting: {sortBy: 'foo', sortOrder: SORT_ORDER.ASC},
  isDataLoaded: true
};

describe('List', () => {
  describe('Table', () => {
    it('should have by default rowsToDisplay 9', () => {
      expect(new List().state.rowsToDisplay).toBe(9);
    });

    it('should render table container with innerRef', () => {
      // given
      const node = shallow(<List {...mockProps} />);

      //then
      const TableContainerNode = node.find(Styled.TableContainer);
      expect(TableContainerNode).toHaveLength(1);
      expect(TableContainerNode.prop('innerRef')).toBe(
        node.instance().containerRef
      );
    });

    it('should render table head', () => {
      // given
      const node = shallow(<List {...mockProps} />);

      // then
      // Table
      expect(node.find(Table)).toHaveLength(1);

      // THead
      const THeadNode = node.find(THead);
      expect(THeadNode).toHaveLength(1);

      // TH
      const THNodes = THeadNode.find(TH);
      expect(THNodes.length).toBe(4);

      // Styled TH
      expect(THeadNode.find(Styled.Th).length).toBe(1);

      // Workflow Definition TH
      expect(THNodes.at(0).contains('Workflow Definition')).toBe(true);
      const CheckboxNode = THNodes.at(0).find(Checkbox);
      expect(CheckboxNode).toHaveLength(1);
      expect(CheckboxNode.prop('isChecked')).toBe(
        node.instance().areAllInstancesSelected()
      );

      // Instance Id TH
      expect(THNodes.at(1).contains('Instance Id')).toBe(true);
      // Instance Id HeaderSortIcon
      const InstanceHeaderSortIconNode = THNodes.at(1).find(HeaderSortIcon);
      expect(InstanceHeaderSortIconNode).toHaveLength(1);
      expect(InstanceHeaderSortIconNode.prop('sortKey')).toBe('id');
      expect(InstanceHeaderSortIconNode.prop('sorting')).toEqual(
        mockProps.sorting
      );
      expect(InstanceHeaderSortIconNode.prop('handleSorting')).toBe(
        mockProps.handleSorting
      );

      // Start Time TH
      expect(THNodes.at(2).contains('Start Time')).toBe(true);
      // Start Time HeaderSortIcon
      const STimeHeaderSortIconNode = THNodes.at(2).find(HeaderSortIcon);
      expect(STimeHeaderSortIconNode.prop('sortKey')).toBe('startDate');
      expect(STimeHeaderSortIconNode.prop('sorting')).toEqual(
        mockProps.sorting
      );
      expect(STimeHeaderSortIconNode.prop('handleSorting')).toBe(
        mockProps.handleSorting
      );

      // End Time TH
      expect(THNodes.at(3).contains('End Time')).toBe(true);
      // Start Time HeaderSortIcon
      const ETimeHeaderSortIconNode = THNodes.at(3).find(HeaderSortIcon);
      expect(ETimeHeaderSortIconNode.prop('sortKey')).toBe('endDate');
      expect(ETimeHeaderSortIconNode.prop('sorting')).toEqual(
        mockProps.sorting
      );
      expect(ETimeHeaderSortIconNode.prop('handleSorting')).toBe(
        mockProps.handleSorting
      );

      // Action TH
      expect(THeadNode.find(Styled.Th).contains('Actions')).toBe(true);
    });

    it('should render table body', () => {
      // given
      const node = shallow(<List {...mockProps} />);

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
        expect(TDNodes.length).toBe(5);

        // Workflow Definition TD
        expect(TDNodes.at(0).find(Checkbox)).toHaveLength(1);
        expect(TDNodes.at(0).contains(getWorkflowName(currentInstance))).toBe(
          true
        );
        // State Icon
        const StateIconNode = TDNodes.at(0).find(StateIcon);
        expect(StateIconNode).toHaveLength(1);
        expect(StateIconNode.prop('instance')).toEqual(currentInstance);

        // Instance Id TD Anchor
        const InstanceAnchorNode = TDNodes.at(1).find(Styled.InstanceAnchor);
        expect(InstanceAnchorNode).toHaveLength(1);
        expect(InstanceAnchorNode.prop('to')).toBe(
          `/instances/${currentInstance.id}`
        );
        expect(InstanceAnchorNode.contains(currentInstance.id)).toBe(true);

        // Start Date TD
        expect(
          TDNodes.at(2).contains(formatDate(currentInstance.startDate))
        ).toBe(true);

        // End Date TD
        expect(
          TDNodes.at(3).contains(formatDate(currentInstance.endDate))
        ).toBe(true);

        // Actions TD
        expect(TDNodes.at(4).find(Actions));
      });
    });

    it('should display a message for empty list when filter has no state', async () => {
      const node = shallow(
        <List {...emptyList} filter={{error: 'mock error message'}} />
      );

      expect(
        node.find('[data-test="empty-message-instances-list"]')
      ).toMatchSnapshot();
    });

    it('should display a empty list message when filter has at least one state', async () => {
      const node = shallow(
        <List
          {...emptyList}
          filter={{error: 'mock error message', active: true}}
        />
      );

      expect(
        node.find('[data-test="empty-message-instances-list"]')
      ).toMatchSnapshot();
    });

    describe('recalculateHeight', () => {
      it('should be called on mount', () => {
        // given
        const recalculateHeightSpy = jest.spyOn(
          List.prototype,
          'recalculateHeight'
        );
        shallow(<List {...mockProps} />);

        // then
        expect(recalculateHeightSpy).toBeCalled();
      });

      it('should only be called when needed', () => {
        // given
        const recalculateHeightSpy = jest.spyOn(
          List.prototype,
          'recalculateHeight'
        );
        const node = shallow(<List {...mockProps} />);
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
        const node = shallow(<List {...mockProps} />);
        node.instance().container = {clientHeight: 38};
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

  describe('Selection', () => {
    let node;
    let instances = [];
    createMockInstances(3, instances);

    beforeEach(() => {
      mockProps.data = instances;
      node = shallow(<List {...mockProps} />);
    });

    describe('highlight', () => {
      it('should highlight all instances when all are selected', () => {
        node.setProps({
          selection: {
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
          selection: {
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

      it('should highlight an instance when it is selection', () => {
        // given
        const selectedInstanceId = 10;
        node.setProps({
          selection: {all: false, excludeIds: [], ids: [selectedInstanceId]}
        });
        expect(node.instance().isSelected(selectedInstanceId)).toBe(true);

        // when
        node.setProps({selection: {all: false, excludeIds: [], ids: []}});

        // then
        expect(node.instance().isSelected(selectedInstanceId)).toBe(false);
      });

      it('should not highlight an instance if excluded from selectAll ', () => {
        // when
        const selectedInstanceId = 10;
        node.setProps({
          selection: {
            all: true,
            ids: [],
            excludeIds: [selectedInstanceId]
          }
        });
        // then
        expect(node.instance().isSelected(selectedInstanceId)).toBe(false);
      });

      it('should set selection.all to true', () => {
        // given
        const onUpdateSelection = jest.fn();

        node.setProps({
          filterCount: 2,
          selection: {
            all: false,
            ids: [10],
            excludeIds: []
          },
          onUpdateSelection: onUpdateSelection
        });

        // when
        const select = node.instance().handleSelectInstance({id: 5});
        select(undefined, true);

        // then
        expect(onUpdateSelection).toBeCalledWith({
          all: true,
          ids: [],
          excludeIds: []
        });
      });

      it('should set selection.all to false', () => {
        // given
        const onUpdateSelection = jest.fn();

        node.setProps({
          filterCount: 2,
          selection: {
            all: true,
            ids: [],
            excludeIds: [10]
          },
          onUpdateSelection: onUpdateSelection
        });

        // when
        const select = node.instance().handleSelectInstance({id: 5});
        select(undefined, false);
        node.update();

        // then
        expect(onUpdateSelection).toBeCalledWith({
          all: false,
          ids: [],
          excludeIds: []
        });
      });

      it('should return that all instances are selected', () => {
        // when
        node.setProps({
          selection: {
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
          selection: {
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
});
