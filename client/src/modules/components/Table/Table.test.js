import React from 'react';
import {shallow} from 'enzyme';

import Table from './Table';
import {ORDER} from 'modules/constants';
import * as Styled from './styled';

describe('Table', () => {
  describe('renderHeader', () => {
    const mockHeaders = {foo: 'foo', bar: 'bar'};

    it('should render header cells based on provided headers', () => {
      // given
      const node = shallow(<Table data={[]} headers={mockHeaders} />);

      // then:
      // it renders a table head and table row
      expect(node.find(Styled.TableHead)).toHaveLength(1);
      expect(node.find(Styled.HeaderRow)).toHaveLength(1);
      // it renders a header cell for each header
      const HeaderCellNodes = node.find(Styled.HeaderCell);
      expect(HeaderCellNodes).toHaveLength(2);
      expect(
        HeaderCellNodes.at(0)
          .childAt(0)
          .text()
      ).toContain('foo');

      expect(
        HeaderCellNodes.at(1)
          .childAt(0)
          .text()
      ).toContain('bar');
    });

    it('should render a sort icon for sortable headers', () => {
      // given
      const config = {
        isSortable: {foo: false, bar: true},
        sortBy: {bar: ORDER.ASC}
      };
      const node = shallow(
        <Table data={[]} headers={mockHeaders} config={config} />
      );

      // then
      const SortIconNode = node.find(Styled.SortIcon);
      expect(SortIconNode).toHaveLength(1);
      expect(SortIconNode.prop('order')).toBe(ORDER.ASC);
      expect(SortIconNode.prop('onClick')).toBeInstanceOf(Function);
    });
  });

  describe('renderBody', () => {
    const data = [
      {name: 'foo', id: 1},
      {name: 'bar', id: 2},
      {name: 'baz', id: 3}
    ];
    const formattedData = data.map(el => ({data: {...el}, view: {...el}}));
    const headers = {name: 'name', id: 'id'};

    it('should render a table row for each data element and cell for each row value', () => {
      // given
      const selectionCheck = jest.fn();
      const node = shallow(
        <Table
          headers={headers}
          data={formattedData}
          config={{selectionCheck}}
        />
      );

      // then
      expect(node.find('tbody')).toHaveLength(1);
      const RowNodes = node.find(Styled.BodyRow);
      expect(RowNodes).toHaveLength(3);
      expect(selectionCheck).toHaveBeenCalledTimes(3);
      selectionCheck.mock.calls.forEach(([callParameter], index) => {
        expect(callParameter).toEqual(data[index]);
      });
      const CellNodes = node.find(Styled.BodyCell);
      expect(CellNodes).toHaveLength(6);
      expect(node).toMatchSnapshot();
    });
  });
});
