/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import Sharing from './Sharing';
import {evaluateEntity} from './service';

jest.mock('./service', () => {
  return {
    evaluateEntity: jest.fn(),
    createLoadReportCallback: jest.fn()
  };
});

jest.mock('components', () => {
  return {
    ReportRenderer: () => <div id="report">ReportRenderer</div>,
    DashboardView: () => <div id="dashboard">DashboardView</div>,
    Button: () => <div>Button</div>,
    Icon: () => <span>Icon</span>,
    LoadingIndicator: props => (
      <div className="sk-circle" {...props}>
        Loading...
      </div>
    )
  };
});

jest.mock('react-router-dom', () => {
  return {
    Link: ({children}) => {
      return <a>{children}</a>;
    }
  };
});

const props = {
  match: {
    params: {
      id: 123
    }
  }
};

it('should render without crashing', () => {
  mount(<Sharing {...props} />);
});

it('should initially load data', () => {
  mount(<Sharing {...props} />);

  expect(evaluateEntity).toHaveBeenCalled();
});

it('should display a loading indicator', () => {
  const node = mount(<Sharing {...props} />);

  expect(node.find('.sk-circle')).toBePresent();
});

it('should display an error message if evaluation was unsuccessful', () => {
  props.match.params.type = 'report';
  const node = mount(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: null
  });

  expect(node.find('.Sharing__error-message')).toBePresent();
});

it('should display an error message if type is invalid', () => {
  props.match.params.type = 'foo';
  const node = mount(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {name: 'foo'}
  });

  expect(node.find('.Sharing__error-message')).toBePresent();
});

it('should have report if everything is fine', () => {
  props.match.params.type = 'report';
  const node = mount(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {name: 'foo'}
  });

  expect(node.find('#report')).toIncludeText('ReportRenderer');
});

it('should retrieve report for the given id', () => {
  props.match.params.type = 'report';
  const node = mount(<Sharing {...props} />);

  expect(evaluateEntity).toHaveBeenCalledWith(123, 'report');
});

it('should display the report name', () => {
  props.match.params.type = 'report';
  const node = mount(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {name: 'My report name'}
  });

  expect(node).toIncludeText('My report name');
});

it('should have dashboard if everything is fine', () => {
  props.match.params.type = 'dashboard';
  const node = mount(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {reportShares: 'foo'}
  });

  expect(node.find('#dashboard')).toIncludeText('DashboardView');
});

it('should retrieve dashboard for the given id', () => {
  props.match.params.type = 'dashboard';
  mount(<Sharing {...props} />);

  expect(evaluateEntity).toHaveBeenCalledWith(123, 'dashboard');
});

it('should display the dashboard name', () => {
  props.match.params.type = 'dashboard';
  const node = mount(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {name: 'My dashboard name'}
  });

  expect(node).toIncludeText('My dashboard name');
});

it('should render a button linking to view mode', () => {
  props.match.params.type = 'dashboard';
  const node = mount(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {name: 'My dashboard name'}
  });

  expect(node).toIncludeText('Button');
});
