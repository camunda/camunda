import React from 'react';
import {mount} from 'enzyme';

import ProgressBarModal from './ProgressBarModal';

jest.mock('components', () => {
  const Modal = ({onConfirm, ...props}) => <div {...props}>{props.children}</div>;
  Modal.Header = props => <div>{props.children}</div>;
  Modal.Content = props => <div>{props.children}</div>;
  Modal.Actions = props => <div>{props.children}</div>;

  const Button = props => {
    return (
      <button {...props} active={props.active ? 'true' : undefined}>
        {props.children}
      </button>
    );
  };

  return {
    Modal,
    Button
  };
});

const validProps = {
  type: 'number',
  reportResult: {
    data: {
      processDefinitionKey: 'a',
      processDefinitionVersion: 1,
      view: {
        entity: 'processInstance',
        operation: 'count',
        property: 'frequency'
      },
      groupBy: {
        type: 'none'
      },
      visualization: 'number'
    },
    result: 12
  },
  configuration: {
    targetValue: {
      active: false,
      values: {
        baseline: 10,
        target: 200
      }
    }
  }
};

jest.mock('./NumberInputs', () => {
  const fct = () => 'NumberInputs';
  fct.sanitizeData = () => 'sanitizedData';
  return fct;
});
jest.mock('./DurationInputs', () => {
  const fct = () => 'DurationInputs';
  fct.sanitizeData = () => 'sanitizedData';
  return fct;
});

it('should render without crashing', () => {
  mount(<ProgressBarModal {...validProps} />);
});

it('should display number inputs for type number', () => {
  const node = mount(<ProgressBarModal {...validProps} type="number" />);

  expect(node).toIncludeText('NumberInputs');
});

it('should display duration inputs for type duration', () => {
  const node = mount(<ProgressBarModal {...validProps} type="duration" />);

  expect(node).toIncludeText('DurationInputs');
});

it('should call the confirm method with the sanitized target values', async () => {
  const spy = jest.fn();
  const node = mount(<ProgressBarModal {...validProps} onConfirm={spy} />);

  await node.setProps({open: true});

  node.find('button[type="primary"]').simulate('click');

  expect(spy).toHaveBeenCalledWith('sanitizedData');
});

it('should disable the confirm button if an input field does not have a valid value', async () => {
  const node = mount(<ProgressBarModal {...validProps} />);

  await node.setProps({open: true});
  await node.setState({isValid: false});

  expect(node.find('button[type="primary"]')).toBeDisabled();
});
