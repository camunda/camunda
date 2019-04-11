/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import Sharing from './Sharing';
import {evaluateEntity} from './service';

jest.mock('./service', () => {
  return {
    evaluateEntity: jest.fn(),
    createLoadReportCallback: jest.fn()
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
  shallow(<Sharing {...props} />);
});

it('should initially load data', () => {
  shallow(<Sharing {...props} />);

  expect(evaluateEntity).toHaveBeenCalled();
});

it('should display a loading indicator', () => {
  const node = shallow(<Sharing {...props} />);

  expect(node.find('LoadingIndicator')).toBePresent();
});

it('should display an error message if evaluation was unsuccessful', () => {
  props.match.params.type = 'report';
  const node = shallow(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: null
  });

  expect(node.find('ErrorPage')).toBePresent();
});

it('should display an error message if type is invalid', () => {
  props.match.params.type = 'foo';
  const node = shallow(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {name: 'foo'}
  });

  expect(node.find('ErrorPage')).toBePresent();
});

it('should have report if everything is fine', () => {
  props.match.params.type = 'report';
  const node = shallow(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {name: 'foo'}
  });

  expect(node.find('ReportRenderer')).toBePresent();
});

it('should retrieve report for the given id', () => {
  props.match.params.type = 'report';
  shallow(<Sharing {...props} />);

  expect(evaluateEntity).toHaveBeenCalledWith(123, 'report');
});

it('should display the report name', () => {
  props.match.params.type = 'report';
  const node = shallow(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {name: 'My report name'}
  });

  expect(node).toIncludeText('My report name');
});

it('should have dashboard if everything is fine', () => {
  props.match.params.type = 'dashboard';
  const node = shallow(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {reportShares: 'foo'}
  });

  expect(node.find('DashboardView')).toBePresent();
});

it('should retrieve dashboard for the given id', () => {
  props.match.params.type = 'dashboard';
  shallow(<Sharing {...props} />);

  expect(evaluateEntity).toHaveBeenCalledWith(123, 'dashboard');
});

it('should display the dashboard name', () => {
  props.match.params.type = 'dashboard';
  const node = shallow(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {name: 'My dashboard name'}
  });

  expect(node).toIncludeText('My dashboard name');
});

it('should render a button linking to view mode', () => {
  props.match.params.type = 'dashboard';
  const node = shallow(<Sharing {...props} />);

  node.setState({
    loading: false,
    evaluationResult: {name: 'My dashboard name'}
  });

  expect(node.find(Button)).toBePresent();
});
