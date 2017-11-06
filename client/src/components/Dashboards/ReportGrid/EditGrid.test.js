import React from 'react';
import {mount} from 'enzyme';

import EditGrid from './EditGrid';
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
const removeMock = jest.fn();

it('should render add button', async () => {
  const node = mount(<EditGrid onReportSelected={selectionMock} onReportRemoved={removeMock} reports={[]}/>);

  expect(node).toIncludeText('Add a report');
  expect(load).not.toHaveBeenCalled();
  expect(node).not.toIncludeText('Select a report from the list');
});

it('should render reports', async () => {
  const node = mount(<EditGrid onReportSelected={selectionMock} onReportRemoved={removeMock} reports={sampleReports}/>);

  expect(node).toIncludeText('r1');
  expect(node).toIncludeText('r2');

  expect(load).not.toHaveBeenCalled();
  expect(node).not.toIncludeText('Select a report from the list');
});

it('should invoke callback on selection', async () => {
  const node = mount(<EditGrid onReportSelected={selectionMock} onReportRemoved={removeMock} reports={[]}/>);

  node.instance().addReport({id: 'stub report'});
  expect(selectionMock).toBeCalledWith({id: 'stub report'}, {
    x: 0,
    y: 0
  }, {
    width: 2,
    height: 2
  });
});

it('should show modal on add click', async () => {
  const node = mount(<EditGrid onReportSelected={selectionMock} onReportRemoved={removeMock} reports={[]}/>);

  node.find('.add-button').simulate('click');
  expect(node).toIncludeText('Select a report from the list');
});
