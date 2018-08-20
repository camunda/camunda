import React from 'react';
import {mount, shallow} from 'enzyme';

import Alerts from './Alerts';
import {loadReports} from './service';
jest.mock('./service', () => {
  return {
    loadReports: jest.fn()
  };
});

jest.mock('components', () => {
  return {EntityList: props => <span id="EntityList">{JSON.stringify(props)}</span>};
});

jest.mock('./AlertModal', () => props => (
  <span>
    EditModal: <span id="ModalProps">{JSON.stringify(props)}</span>
  </span>
));

jest.mock('services', () => {
  return {
    formatters: {
      duration: () => '14 seconds',
      frequency: () => '12'
    }
  };
});

const reports = [
  {id: '1', data: {visualization: 'table', view: {property: 'frequency'}}, name: 'Report 1'},
  {id: '2', data: {visualization: 'number', view: {property: 'duration'}}, name: 'Report 2'}
];
loadReports.mockReturnValue(reports);

it('should load existing reports', async () => {
  await mount(<Alerts />);

  expect(loadReports).toHaveBeenCalled();
});

it('should only save single number reports', async () => {
  const node = mount(<Alerts />);

  await node.instance().componentDidMount();

  expect(node.state('reports').map(report => report.id)).toEqual(['2']);
});

it('should format durations with value and unit', async () => {
  const wrapper = shallow(<Alerts />);

  const newAlert = {
    name: 'New Alert',
    email: '',
    reportId: '2',
    thresholdOperator: '>',
    threshold: '100',
    checkInterval: {
      value: '10',
      unit: 'minutes'
    },
    reminder: null,
    fixNotification: false
  };
  await wrapper.instance().componentDidMount();

  const node = shallow(wrapper.find('EntityList').prop('renderCustom')(newAlert));

  expect(node).toIncludeText('14 seconds');
});
