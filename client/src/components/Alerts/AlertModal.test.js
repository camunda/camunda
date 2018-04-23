import React from 'react';
import {mount} from 'enzyme';

import AlertModal from './AlertModal';
import {emailNotificationIsEnabled, convertDurationToObject} from './service';

jest.mock('./service', () => {
  return {
    emailNotificationIsEnabled: jest.fn(),
    convertDurationToSingleNumber: jest.fn().mockReturnValue(723),
    convertDurationToObject: jest.fn().mockReturnValue({value: '14', unit: 'seconds'})
  };
});

jest.mock('./ThresholdInput', () => () => 'ThresholdInput');

jest.mock('components', () => {
  const Modal = props => <div id="modal">{props.children}</div>;
  Modal.Header = props => <div id="modal_header">{props.children}</div>;
  Modal.Content = props => <div id="modal_content">{props.children}</div>;
  Modal.Actions = props => <div id="modal_actions">{props.children}</div>;

  const Select = props => {
    const allowedProps = {...props};
    delete allowedProps.isInvalid;
    return <select {...allowedProps}>{props.children}</select>;
  };
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {
    Modal,
    ErrorMessage: props => <div {...props}>{props.children}</div>,
    Button: props => <button {...props}>{props.children}</button>,
    Input: props => {
      const allowedProps = {...props};
      delete allowedProps.isInvalid;
      return <input {...allowedProps} />;
    },
    Select
  };
});

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
};

const reports = [
  {id: '5', name: 'Some Report', data: {view: {property: 'frequency'}}},
  {id: '8', name: 'Nice report', data: {view: {property: 'frequency'}}}
];

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
  node.setState(
    {email: 'this is not a valid email'},
    expect(node.find('button[type="primary"]')).toBeDisabled
  );
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
  node.setState(
    {
      checkInterval: {
        value: '-7',
        unit: 'seconds'
      }
    },
    expect(node.find('button[type="primary"]')).toBeDisabled
  );
});

it('should show warning that email is not configured', async () => {
  emailNotificationIsEnabled.mockReturnValue(false);
  const node = await mount(<AlertModal reports={reports} />);
  await node.update();

  expect(
    node.find('.AlertModal__configuration-warning').hasClass('AlertModal__configuration-warning')
  ).toBe(true);
});

it('should not display warning if email is configured', async () => {
  emailNotificationIsEnabled.mockReturnValue(true);
  const node = await mount(<AlertModal reports={reports} />);
  await node.update();

  expect(node.find('.AlertModal__configuration-warning').exists()).toBe(false);
});

it('should set isInvalid property for input if value is invalid', async () => {
  const node = await mount(<AlertModal reports={reports} />);
  node.setState({name: ''});
  await node.update();

  expect(
    node
      .find('.AlertModal__input')
      .first()
      .props()
  ).toHaveProperty('isInvalid', true);
});

it('should convert a duration threshold when opening', async () => {
  const node = await mount(
    <AlertModal
      reports={[
        {
          id: '8',
          name: 'Nice report',
          data: {view: {property: 'duration'}}
        }
      ]}
    />
  );

  node.setProps({
    alert: {
      name: 'New Alert',
      id: '1234',
      email: '',
      reportId: '8',
      thresholdOperator: '>',
      threshold: '14000',
      checkInterval: {
        value: '10',
        unit: 'minutes'
      },
      reminder: null,
      fixNotification: false
    }
  });

  expect(node.state('threshold')).toEqual({value: '14', unit: 'seconds'});
  expect(convertDurationToObject).toHaveBeenCalledWith('14000');
});

it('should convert a duration threshold when confirming', async () => {
  const spy = jest.fn();
  const node = await mount(<AlertModal reports={reports} onConfirm={spy} />);
  node.setState({threshold: {value: '723', unit: 'milliseconds'}});

  node.instance().confirm();

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].threshold).toBe(723);
});

it('should contain a threshold input', () => {
  const node = mount(<AlertModal reports={reports} />);

  expect(node).toIncludeText('ThresholdInput');
});
