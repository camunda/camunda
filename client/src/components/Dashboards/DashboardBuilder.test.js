import React from 'react';
import {mount} from 'enzyme';

import DashboardBuilder from './DashboardBuilder';

jest.mock('./AddButton', () => () => <div>AddButton</div>);
jest.mock('./DashboardReport', () => ({id}) => <div>DashboardReport {id}</div>);

const reports = [
  {
    position: {x: 0, y: 0},
    dimensions: {width: 3, height: 1},
    id: '1'
  },
  {
    position: {x: 2, y: 0},
    dimensions: {width: 1, height: 4},
    id: '2'
  },
  {
    position: {x: 3, y: 1},
    dimensions: {width: 2, height: 2},
    id: '3'
  }
];

it('should contain an AddButton', () => {
  const node = mount(<DashboardBuilder reports={reports} />);

  expect(node).toIncludeText('AddButton');
});

it('should contain reports', () => {
  const node = mount(<DashboardBuilder reports={reports} />);

  expect(node).toIncludeText('DashboardReport 1');
  expect(node).toIncludeText('DashboardReport 2');
  expect(node).toIncludeText('DashboardReport 3');
});

it('should place the addButton where is no report', () => {
  const node = mount(<DashboardBuilder reports={reports} />);

  const addButtonPosition = node.instance().getAddButtonPosition();

  expect(addButtonPosition.x).toBe(5);
  expect(addButtonPosition.y).toBe(0);
});
