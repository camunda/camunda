import React from 'react';
import {mount} from 'enzyme';

import ReportModal from './ReportModal';
import {loadEntity} from 'services';

jest.mock('components', () => {
  const Modal = props => <div id="Modal">{props.open && props.children}</div>;
  Modal.Header = props => <div id="modal_header">{props.children}</div>;
  Modal.Content = props => <div id="modal_content">{props.children}</div>;
  Modal.Actions = props => <div id="modal_actions">{props.children}</div>;

  const Select = props => <select {...props}>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {
    Modal,
    Select,
    Button: props => <button {...props}>{props.children}</button>,
    ControlGroup: props => <div>{props.children}</div>,
    Input: ({isInvalid, ...props}) => <input {...props} />,
    ErrorMessage: props => <div {...props} />
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

it('should render a select element with the available reports as options', () => {
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

  expect(node.find('select')).toBePresent();
  expect(node.find('select')).toIncludeText('Please select...');
  expect(node.find('select')).toIncludeText('Report A');
  expect(node.find('select')).toIncludeText('Report B');
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

  expect(node.find('select')).toIncludeText('No reports created yet');
  expect(node.find('select')).not.toIncludeText('Please select...');
});

it('should show a loading message while loading available reports', () => {
  const node = mount(<ReportModal />);

  expect(node.find('select')).toIncludeText('loading...');
  expect(node.find('select')).not.toIncludeText('Please select...');
  expect(node.find('select')).not.toIncludeText('No reports created yet');
});

it("should truncate report name if it's longer than 50 signs", () => {
  const node = mount(<ReportModal />);

  node.setState({
    availableReports: [
      {
        id: 'anId',
        name: 'a super long name that should be definitely longer than 50 signs.'
      }
    ]
  });

  expect(node.find('option[value="anId"]').text().length).toBeLessThanOrEqual(50);
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

it('should disable the dropdown when external mode is enabled', () => {
  const node = mount(<ReportModal />);

  node.setState({external: true});

  expect(node.find('select')).toBeDisabled();
});
