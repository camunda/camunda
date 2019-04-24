/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ReportModal from './ReportModal';
import {loadEntities} from 'services';
import {Button} from 'components';

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    loadEntities: jest.fn().mockReturnValue([])
  };
});

it('should load the available reports', () => {
  shallow(<ReportModal />);

  expect(loadEntities).toHaveBeenCalled();
});

it('should render a Typeahead element with the available reports as options', () => {
  const node = shallow(<ReportModal />);

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

  const props = node.find('Typeahead').props();

  expect(node.find('Typeahead')).toBePresent();
  expect(props.placeholder).toBe('Select a Report');
  expect(props.values[0].name).toBe('Report A');
  expect(props.values[1].name).toBe('Report B');
});

it('should call the callback when adding a report', () => {
  const spy = jest.fn();
  const node = shallow(<ReportModal confirm={spy} />);

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

  node
    .find(Button)
    .at(1)
    .simulate('click');

  expect(spy).toHaveBeenCalledWith({
    id: 'a'
  });
});

it('should show only "No reports created yet" option if no reports are available', async () => {
  const node = await shallow(<ReportModal />);

  expect(node.find('p').at(0)).toIncludeText('No reports created yet');
});

it('should show a loading message while loading available reports', () => {
  const node = shallow(<ReportModal />);

  expect(node.find('LoadingIndicator')).toBePresent();
});

it("should truncate report name if it's longer than 90 signs", () => {
  const node = shallow(<ReportModal />);

  const report = {
    id: 'a',
    name:
      'a super long name that should be definitely longer longer longer longer longer longer than 90 signs.'
  };

  node.setState({
    availableReports: [report]
  });

  const truncatedText = node
    .find('Typeahead')
    .props()
    .formatter(report);

  expect(truncatedText.length).toBeLessThanOrEqual(90);
});

it('should contain an Add External Source field', () => {
  const node = shallow(<ReportModal />);

  expect(node.find('.ReportModal__externalSourceLink')).toIncludeText('Add External Source');
});

it('should contain a text input field if in external source mode', () => {
  const node = shallow(<ReportModal />);

  node.setState({external: true});

  expect(node.find('.externalInput')).toBePresent();
});

it('should  disable the submit button if the url does not start with http in external mode', () => {
  const node = shallow(<ReportModal />);

  node.setState({external: true, externalUrl: 'Dear computer, please show me a report. Thanks.'});

  expect(node.find(Button).at(1)).toBeDisabled();
});

it('should disable the typeahead when external mode is enabled', () => {
  const node = shallow(<ReportModal />);

  node.setState({external: true, availableReports: [{name: 'test name'}]});

  expect(node.find('Typeahead')).toBeDisabled();
});
