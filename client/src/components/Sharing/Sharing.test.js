/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {ReportRenderer, InstanceCount} from 'components';

import {Sharing} from './Sharing';
import {evaluateEntity} from './service';

jest.mock('./service', () => {
  return {
    evaluateEntity: jest.fn(),
    createLoadReportCallback: jest.fn(),
  };
});

const props = {
  match: {
    params: {
      id: 123,
    },
  },
  location: {search: ''},
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should render without crashing', () => {
  shallow(<Sharing {...props} />);
});

it('should initially load data', () => {
  shallow(<Sharing {...props} />);

  expect(evaluateEntity).toHaveBeenCalled();
});

it('should display a loading indicator', () => {
  const node = shallow(<Sharing {...props} mightFail={() => {}} />);

  expect(node.find('LoadingIndicator')).toExist();
});

it('should display an error message if evaluation was unsuccessful', () => {
  props.match.params.type = 'report';
  const node = shallow(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: null,
  });

  expect(node.find('ErrorPage')).toExist();
});

it('should pass the error to reportRenderer if evaluation fails', async () => {
  props.match.params.type = 'report';
  const testError = {status: 400, message: 'testError', reportDefinition: {}};
  const mightFail = (promise, cb, err, final) => {
    err(testError);
    final();
  };

  const node = await shallow(<Sharing {...props} mightFail={mightFail} />);
  await flushPromises();

  expect(node.find(ReportRenderer).prop('error')).toEqual(testError);
});

it('should display an error message if type is invalid', () => {
  props.match.params.type = 'foo';
  const node = shallow(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {name: 'foo'},
  });

  expect(node.find('ErrorPage')).toExist();
});

it('should have report if everything is fine', () => {
  props.match.params.type = 'report';
  const node = shallow(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {name: 'foo'},
  });

  expect(node.find(ReportRenderer)).toExist();
});

it('should retrieve report for the given id', () => {
  props.match.params.type = 'report';
  shallow(<Sharing {...props} />);

  expect(evaluateEntity).toHaveBeenCalledWith(123, 'report', undefined);
});

it('should display the report name and include report details', () => {
  props.match.params.type = 'report';
  const node = shallow(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {name: 'My report name'},
  });

  expect(node.find('EntityName')).toExist();
  expect(node.find('EntityName').prop('children')).toBe('My report name');
  expect(node.find('EntityName').prop('details').props.report).toEqual({name: 'My report name'});
});

it('should include the InstanceCount for reports', () => {
  props.match.params.type = 'report';
  const node = shallow(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {name: 'My report name'},
  });

  expect(node.find(InstanceCount)).toExist();
  expect(node.find(InstanceCount).prop('report')).toEqual({name: 'My report name'});
});

it('should have dashboard if everything is fine', () => {
  props.match.params.type = 'dashboard';
  const node = shallow(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {reportShares: 'foo'},
  });

  expect(node.find('DashboardRenderer')).toExist();
});

it('should include filters on a dashboard', () => {
  props.match.params.type = 'dashboard';
  const node = shallow(
    <Sharing
      {...props}
      location={{
        search: '?filter=%5B%7B%22type%22%3A%22runningInstancesOnly%22%2C%22data%22%3Anull%7D%5D',
      }}
    />
  );

  node.setState({
    loading: false,
    evaluationResult: {reportShares: 'foo'},
  });

  expect(node.find('DashboardRenderer').prop('filter')).toEqual([
    {data: null, type: 'runningInstancesOnly'},
  ]);
});

it('should retrieve dashboard for the given id', () => {
  props.match.params.type = 'dashboard';
  shallow(<Sharing {...props} />);

  expect(evaluateEntity).toHaveBeenCalledWith(123, 'dashboard', undefined);
});

it('should display the dashboard name and last modification info', () => {
  props.match.params.type = 'dashboard';
  const node = shallow(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {name: 'My dashboard name'},
  });

  expect(node.find('EntityName')).toExist();
  expect(node.find('EntityName').prop('children')).toBe('My dashboard name');
  expect(node.find('EntityName').prop('details').props.entity).toEqual({name: 'My dashboard name'});
});

it('should render an href directing to view mode ignoring /external sub url', () => {
  props.match.params.type = 'dashboard';
  delete window.location;
  window.location = new URL('http://example.com/subUrl/external/#/share/dashboard/shareId');

  const node = shallow(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {id: 'dashboardId'},
  });

  expect(node.find('.Button').prop('href')).toBe(
    'http://example.com/subUrl/#/dashboard/dashboardId/'
  );
});

it('should show a compact version and lock scroll when a shared report is embedded', () => {
  props.match.params.type = 'report';
  props.location.search = '?mode=embed';

  const node = shallow(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {name: 'My report name'},
  });

  expect(node.find('DiagramScrollLock')).toExist();
  expect(node.find('.iconLink')).toExist();
  expect(node.find('.Sharing')).toHaveClassName('compact');
  expect(node.find('.title-button')).toHaveClassName('small');
});

it('should add report classname to the sharing container', () => {
  props.match.params.type = 'report';
  props.location.search = '?header=hidden';

  const node = shallow(<Sharing {...props} />);
  node.setState({
    loading: false,
    evaluationResult: {name: 'My report name', id: 'aReportId'},
  });

  expect(node.find('.Sharing')).toHaveClassName('report');
});

it('should only show the title and hide the link to Optimize', () => {
  props.location.search = '?header=titleOnly';

  const node = shallow(<Sharing {...props} />);
  node.setState({
    loading: false,
    evaluationResult: {name: 'My report name', id: 'aReportId'},
  });

  expect(node.find(InstanceCount)).toExist();
  expect(node.find('EntityName')).toExist();
  expect(node.find('.title-button')).not.toExist();
});

it('should only show the link to Optimize and hide the title', () => {
  props.location.search = '?header=linkOnly';

  const node = shallow(<Sharing {...props} />);
  node.setState({
    loading: false,
    evaluationResult: {name: 'My report name', id: 'aReportId'},
  });

  expect(node.find('.title-button')).toExist();
  expect(node.find('EntityName')).not.toExist();
});

it('should hide the whole header if specified', () => {
  props.match.params.type = 'report';
  props.location.search = '?header=hidden';

  const node = shallow(<Sharing {...props} />);
  node.setState({
    loading: false,
    evaluationResult: {name: 'My report name', id: 'aReportId'},
  });

  expect(node.find('.cds--header')).not.toExist();
});
