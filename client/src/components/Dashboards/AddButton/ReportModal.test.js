/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ReportModal from './ReportModal';
import {Button, Typeahead} from 'components';
import {loadReports} from 'services';

jest.mock('react-router-dom', () => {
  const rest = jest.requireActual('react-router-dom');
  return {
    ...rest,
    withRouter: a => a
  };
});

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    loadReports: jest.fn().mockReturnValue([])
  };
});

const props = {
  location: {pathname: '/dashboard/1'}
};

it('should load the available reports', () => {
  shallow(<ReportModal {...props} />);

  expect(loadReports).toHaveBeenCalled();
});

it('should load only reports in the same collection', () => {
  shallow(<ReportModal location={{pathname: '/collection/123/dashboard/1'}} />);

  expect(loadReports).toHaveBeenCalledWith('123');
});

it('should render a Typeahead element with the available reports as options', () => {
  const node = shallow(<ReportModal {...props} />);

  node.setState({
    availableReports: [
      {
        id: 'a',
        name: 'Report A'
      },
      {
        id: 'b',
        name: 'Report B'
      }
    ]
  });

  expect(node.find('Typeahead')).toMatchSnapshot();
});

it('should call the callback when adding a report', () => {
  const spy = jest.fn();
  const node = shallow(<ReportModal {...props} confirm={spy} />);

  node.setState({
    availableReports: [
      {
        id: 'a',
        name: 'Report A'
      },
      {
        id: 'b',
        name: 'Report B'
      }
    ],
    selectedReportId: 'a'
  });

  node.find({variant: 'primary'}).simulate('click');

  expect(spy).toHaveBeenCalledWith({
    id: 'a'
  });
});

it('should disable typeahead if no reports created yet', async () => {
  const node = await shallow(<ReportModal {...props} />);

  expect(node.find('Typeahead')).toBeDisabled();
});

it('should show a loading message while loading available reports', () => {
  const node = shallow(<ReportModal {...props} />);

  expect(node.find('LoadingIndicator')).toExist();
});

it("should truncate report name if it's longer than 90 signs", () => {
  const node = shallow(<ReportModal {...props} />);

  const report = {
    id: 'a',
    name:
      'a super long name that should be definitely longer longer longer longer longer longer than 90 signs.'
  };

  node.setState({
    availableReports: [report]
  });

  expect(node.find(Typeahead.Option).text().length).toBeLessThanOrEqual(90);
});

it('should contain an Add External Source field', () => {
  const node = shallow(<ReportModal {...props} />);

  expect(node.find(Button).at(1)).toIncludeText('Add External Source');
});

it('should hide the typeahead when external mode is enabled', () => {
  const node = shallow(<ReportModal {...props} />);

  node.setState({external: true});

  expect(node.find('Typeahead')).not.toExist();
});

it('should contain a text input field if in external source mode', () => {
  const node = shallow(<ReportModal {...props} />);

  node.setState({external: true});

  expect(node.find('.externalInput')).toExist();
});

it('should  disable the submit button if the url does not start with http in external mode', () => {
  const node = shallow(<ReportModal {...props} />);

  node.setState({external: true, externalUrl: 'Dear computer, please show me a report. Thanks.'});

  expect(node.find({variant: 'primary'})).toBeDisabled();
});
