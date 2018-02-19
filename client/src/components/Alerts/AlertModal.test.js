import React from 'react';
import {mount} from 'enzyme';

import AlertModal from './AlertModal';
import {emailNotificationIsEnabled} from './service'

jest.mock('./service', () => {
  return {
    emailNotificationIsEnabled: jest.fn()
  }
});

jest.mock('components', () =>{
  const Modal = props => <div id='modal'>{props.children}</div>;
  Modal.Header = props => <div id='modal_header'>{props.children}</div>;
  Modal.Content = props => <div id='modal_content'>{props.children}</div>;
  Modal.Actions = props => <div id='modal_actions'>{props.children}</div>;

  const Select = props => <select {...props}>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {
  Modal,
  Button: props => <button {...props}>{props.children}</button>,
  Input: props => <input {...props}/>,
  Select
}});

const alert = {
  id: '71395',
  name: 'Sample Alert',
  email: 'test@camunda.com',
  reportId: '8',
  thresholdOperator: '<',
  threshold: 37,
  checkInterval: {
    value: 1,
    unit: 'hours'
  },
  reminder: null,
  fixNotification: true
}

const reports = [
  {id: '5', name: 'Some Report'},
  {id: '8', name: 'Nice report'},
]

it('should apply the alert property to the state when changing props', () => {
  const node = mount(<AlertModal reports={reports} />);

  node.setProps({alert});

  expect(node.state()).toEqual({
    id: '71395',
    name: 'Sample Alert',
    email: 'test@camunda.com',
    reportId: '8',
    thresholdOperator: '<',
    threshold: '37',
    checkInterval: {
      value: '1',
      unit: 'hours'
    },
    reminder: null,
    fixNotification: true,
    errorInput: null
  });
});

it('should show available reports passed in as property', () => {
  const node = mount(<AlertModal reports={reports} />);

  expect(node).toIncludeText('Some Report');
  expect(node).toIncludeText('Nice report');
});

it('should call the onConfirm method', () => {
  const spy = jest.fn();
  const node = mount(<AlertModal reports={reports} onConfirm={spy} />);

  node.setProps({alert});

  node.find('button[type="primary"]').simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should disable the submit button if the name is empty', () => {
  const node = mount(<AlertModal reports={reports} />);

  node.setProps({alert});
  node.setState({name: ''}, expect(node.find('button[type="primary"]')).toBeDisabled);
});

it('should disable the submit button if the email is not valid', () => {
  const node = mount(<AlertModal reports={reports} />);

  node.setProps({alert});
  node.setState({email: 'this is not a valid email'}, expect(node.find('button[type="primary"]')).toBeDisabled);
});

it('should disable the submit button if no report is selected', () => {
  const node = mount(<AlertModal reports={reports} />);

  node.setProps({alert});
  node.setState({reportId: ''}, expect(node.find('button[type="primary"]')).toBeDisabled);
});

it('should disable the submit button if the threshold is not a number', () => {
  const node = mount(<AlertModal reports={reports} />);

  node.setProps({alert});
  node.setState({threshold: 'five'}, expect(node.find('button[type="primary"]')).toBeDisabled);
});

it('should disable the submit button if the check interval is negative', () => {
  const node = mount(<AlertModal reports={reports} />);

  node.setProps({alert});
  node.setState({checkInterval: {
    value: '-7',
    unit: 'seconds'
  }}, expect(node.find('button[type="primary"]')).toBeDisabled);
});

it('should show warning that email is not configured', async () => {
  emailNotificationIsEnabled.mockReturnValue(false);
  const node = await mount(<AlertModal reports={reports} />);
  await node.update();

  expect(node.find('.AlertModal__configuration-warning').hasClass('AlertModal__configuration-warning')).toBe(true);
});

it('should not display warning if email is configured', async () => {
  emailNotificationIsEnabled.mockReturnValue(true);
  const node = await mount(<AlertModal reports={reports} />);
  await node.update();

  expect(node.find('.AlertModal__configuration-warning').exists()).toBe(false);
});
