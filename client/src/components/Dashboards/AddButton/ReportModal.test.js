/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import ReportModal from './ReportModal';
import {loadEntity} from 'services';

jest.mock('components', () => {
  const Modal = props => <div id="Modal">{props.open && props.children}</div>;
  Modal.Header = props => <div id="modal_header">{props.children}</div>;
  Modal.Content = props => <div id="modal_content">{props.children}</div>;
  Modal.Actions = props => <div id="modal_actions">{props.children}</div>;

  const Typeahead = props => (
    <div>
      {props.values.map((val, i) => (
        <li key={i}>{props.formatter(val)}</li>
      ))}
      <div>{JSON.stringify(props)}</div>
    </div>
  );

  return {
    Modal,
    Button: props => <button {...props}>{props.children}</button>,
    ControlGroup: props => <div>{props.children}</div>,
    Input: ({isInvalid, ...props}) => <input {...props} />,
    Labeled: props => (
      <div>
        <label id={props.id}>{props.label}</label>
        {props.children}
      </div>
    ),
    ErrorMessage: props => <div {...props} />,
    LoadingIndicator: () => <div className="sk-circle">Loading...</div>,
    Typeahead
  };
});

jest.mock('services', () => {
  return {
    loadEntity: jest.fn().mockReturnValue([])
  };
});

it('should load the available reports', () => {
  mount(<ReportModal />);

  expect(loadEntity).toHaveBeenCalled();
});

it('should render a Typeahead element with the available reports as options', () => {
  const node = mount(<ReportModal />);

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

  expect(node.find('Typeahead')).toBePresent();
  expect(node.find('Typeahead')).toIncludeText('Please select Report');
  expect(node.find('Typeahead')).toIncludeText('Report A');
  expect(node.find('Typeahead')).toIncludeText('Report B');
});

it('should call the callback when adding a report', () => {
  const spy = jest.fn();
  const node = mount(<ReportModal confirm={spy} />);

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

  node.find('button[type="primary"]').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    id: 'a'
  });
});

it('should show only "No reports created yet" option if no reports are available', async () => {
  const node = await mount(<ReportModal />);

  expect(node).toIncludeText('No reports created yet');
  expect(node).not.toIncludeText('Please select Report');
});

it('should show a loading message while loading available reports', () => {
  const node = mount(<ReportModal />);

  expect(node.find('.sk-circle')).toBePresent();
  expect(node).not.toIncludeText('Please select Report');
  expect(node).not.toIncludeText('No reports created yet');
});

it("should truncate report name if it's longer than 90 signs", () => {
  const node = mount(<ReportModal />);

  node.setState({
    availableReports: [
      {
        id: 'anId',
        name:
          'a super long name that should be definitely longer longer longer longer longer longer than 90 signs.'
      }
    ]
  });

  expect(node.find('Typeahead li').text().length).toBeLessThanOrEqual(90);
});

it('should contain an Add External Source field', () => {
  const node = mount(<ReportModal />);

  expect(node).toIncludeText('Add External Source');
});

it('should contain a text input field if in external source mode', () => {
  const node = mount(<ReportModal />);

  node.setState({external: true});

  expect(node.find('input[name="externalInput"]')).toBePresent();
});

it('should show an error and disable the submit button if the url does not start with http in external mode', () => {
  const node = mount(<ReportModal />);

  node.setState({external: true, externalUrl: 'Dear computer, please show me a report. Thanks.'});

  expect(node.find('button[type="primary"]')).toBeDisabled();
  expect(node).toIncludeText('URL has to start with http:// or https://');
});

it('should disable the typeahead when external mode is enabled', () => {
  const node = mount(<ReportModal />);

  node.setState({external: true, availableReports: [{name: 'test name'}]});

  expect(node.find('Typeahead')).toBeDisabled();
});
