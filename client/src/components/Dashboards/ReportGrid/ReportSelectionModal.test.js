import React from 'react';
import {mount} from 'enzyme';

import ReportSelectionModal from './ReportSelectionModal';
import {load} from '../../EntityList/service';

jest.mock('../../EntityList/service', () => {
  return {
    load: jest.fn()
  }
});

const sampleReports = [
  {
    id: 1,
    name : 'r1',
    position: {x: 0, y: 0},
    dimensions: {width: 1, height: 1}
  },
  {
    id: 2,
    name: 'r2',
    position: {x: 0, y: 2},
    dimensions: {width: 1, height: 1}
  }
];

load.mockReturnValue(sampleReports);

const selectionMock = jest.fn();
const closeMock = jest.fn();

it('should render select with reports', async () => {
  const node = mount(<ReportSelectionModal onSelectReport={selectionMock}
                                           onCloseModal={closeMock}
  /> );

  expect(load).toHaveBeenCalled();
  await node.instance().loadEntities();

  expect(node).toIncludeText('Select a report from the list');
  expect(node).toIncludeText('r1');
  expect(node).toIncludeText('r2');
});

it('should callback on selection', async () => {
  const node = mount(<ReportSelectionModal onSelectReport={selectionMock}
                                           onCloseModal={closeMock}
  /> );

  node.find('select').simulate('change', {target : 'test'});

  expect(selectionMock).toHaveBeenCalled();
});

it('should callback on close', async () => {
  const node = mount(<ReportSelectionModal onSelectReport={selectionMock}
                                           onCloseModal={closeMock}
  /> );

  node.find('.close').simulate('click');

  expect(closeMock).toHaveBeenCalled();
});



