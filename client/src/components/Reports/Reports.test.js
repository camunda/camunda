import React from 'react';
import {mount} from 'enzyme';

import Reports from './Reports';

import {loadReports, remove, create} from './service';

const sampleReport = {
  id: '1',
  name: 'Test Report',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
};

jest.mock('./service', () => {return {
  loadReports: jest.fn(),
  remove: jest.fn(),
  create: jest.fn()
}});
jest.mock('react-router-dom', () => {return {
  Redirect: ({to}) => {return <div>REDIRECT to {to}</div>},
}});
jest.mock('moment', () => () => {return {
  format: () => 'some date'
}});
jest.mock('../Table', () => {return {
  Table: ({data}) => <table><tbody><tr><td>{JSON.stringify(data)}</td></tr></tbody></table>
}});

loadReports.mockReturnValue([sampleReport]);

it('should display a loading indicator', () => {
  const node = mount(<Reports />);

  expect(node).toIncludeText('loading');
});

it('should initially load data', () => {
  mount(<Reports />);

  expect(loadReports).toHaveBeenCalled();
});

it('should display a table with the results', () => {
  const node = mount(<Reports />);

  node.setState({
    loaded: true,
    data: [sampleReport]
  });

  expect(node).toIncludeText(sampleReport.name);
  expect(node).toIncludeText(sampleReport.lastModifier);
  expect(node).toIncludeText('some date');
});

it('should call new report on click on the new report button and redirect to the new report', async () => {
  create.mockReturnValueOnce('2');
  const node = mount(<Reports />);

  await node.find('button').simulate('click');

  expect(node).toIncludeText('REDIRECT to /report/2/edit');
});

it('should allow to delete reports', () => {
  const node = mount(<Reports />);

  node.instance().deleteReport(2)();

  expect(remove).toHaveBeenCalledWith(2);
});
