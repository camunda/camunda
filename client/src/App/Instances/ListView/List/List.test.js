import React from 'react';
import {shallow} from 'enzyme';

import {PANE_STATE} from 'modules/components/SplitPane/Pane/constants';
import Table from 'modules/components/Table';

import List from './List';
import * as Styled from './styled';

const instance = {
  activities: [],
  businessKey: 'orderProcess',
  endDate: null,
  id: '4294968496',
  incidents: [],
  startDate: '2018-06-21T11:13:31.094+0000',
  state: 'ACTIVE',
  workflowId: '2'
};

describe('List', () => {
  const mockProps = {
    data: [instance],
    onSelectionUpdate: jest.fn(),
    onEntriesPerPageChange: jest.fn(),
    handleSorting: jest.fn(),
    selection: {
      exclusionList: new Set(),
      query: {},
      list: [{}]
    },
    paneState: PANE_STATE.DEFAULT,
    sortBy: {foo: 'asc'}
  };

  it('should have by default rowsToDisplay null', () => {
    expect(new List().state.rowsToDisplay).toBe(null);
  });

  it('should render a table with data', () => {
    // given
    const node = shallow(<List {...mockProps} />);
    const headers = {foo: 'bar'};
    const config = {baz: 2};
    node.instance().getTableHeaders = jest.fn(() => headers);
    node.instance().getTableData = jest.fn(() => mockProps.data);
    node.instance().getTableConfig = jest.fn(() => config);

    // when
    node.setState({rowsToDisplay: 10});
    node.update();

    // then
    const TableContainerNode = node.find(Styled.TableContainer);
    expect(TableContainerNode).toHaveLength(1);
    expect(TableContainerNode.prop('innerRef')).toBe(
      node.instance().containerRef
    );
    const TableNode = TableContainerNode.find(Table);
    expect(TableNode.prop('headers')).toEqual(headers);
    expect(TableNode.prop('data')).toEqual(mockProps.data);
    expect(TableNode.prop('config')).toEqual(config);
    expect(TableNode.prop('handleSorting')).toBe(mockProps.handleSorting);
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

      // when component updates but paneState does not change
      node.setProps({paneState: PANE_STATE.DEFAULT});
      // then recalculateHeight should not be called
      expect(recalculateHeightSpy).not.toBeCalled();

      // when component updates but paneState is COLLAPSED
      node.setProps({paneState: PANE_STATE.COLLAPSED});
      // then recalculateHeight should not be called
      expect(recalculateHeightSpy).not.toBeCalled();

      // when component updates and paneState changed and is not COLLAPSED
      node.setProps({paneState: PANE_STATE.EXPANDED});
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

  describe('getInstanceAnchor', () => {
    it('renders a table row with a link to the instance page', () => {
      const node = shallow(<List {...mockProps} />);
      const formatTableRow = node.instance().formatTableRow;
      const rowData = formatTableRow(instance);

      expect(rowData.id.props.to).toBe(`/instances/${instance.id}`);
      expect(rowData.id.props.children).toBe(instance.id);
    });
  });

  describe('getTableConfig', () => {
    it('should return table configuration', () => {
      // given
      const node = shallow(<List {...mockProps} />);
      const tableConfig = node.instance().getTableConfig();

      // then
      expect(typeof tableConfig.selectionCheck).toBe('function');
      expect(tableConfig.isSortable).toEqual({
        workflowId: false,
        id: true,
        startDate: true,
        endDate: true,
        actions: false
      });
      expect(tableConfig.sortBy).toEqual(mockProps.sortBy);
      expect(tableConfig).toMatchSnapshot();
    });

    it('should return a selectionCheck function', () => {
      // given
      const node = shallow(<List {...mockProps} />);
      const tableConfig = node.instance().getTableConfig();
      const isSelectedMock = jest.spyOn(node.instance(), 'isSelected');

      // when
      tableConfig.selectionCheck({id: 'foo'});

      // then
      expect(isSelectedMock).toBeCalledWith('foo');
    });
  });
});
