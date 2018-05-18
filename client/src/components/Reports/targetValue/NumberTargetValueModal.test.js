import React from 'react';
import {mount} from 'enzyme';

import TargetValueModal from './NumberTargetValueModal';

jest.mock('services', () => {
  return {
    formatters: {
      convertDurationToSingleNumber: () => 123
    }
  };
});

jest.mock('components', () => {
  const Modal = props => <div {...props}>{props.children}</div>;
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

  const LabeledInput = ({children, isInvalid, ...props}) => (
    <div>
      <input {...props} />
      {children}
    </div>
  );

  const Select = props => <select {...props}>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  const ErrorMessage = function ErrorMessage(props) {
    return <div {...props}>{props.children}</div>;
  };

  return {
    Modal,
    Button,
    LabeledInput,
    Select,
    ErrorMessage
  };
});

const validProps = {
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

it('should render without crashing', () => {
  mount(<TargetValueModal {...validProps} />);
});

it('should display the current target values', async () => {
  const node = mount(<TargetValueModal {...validProps} />);

  await node.setProps({open: true});

  expect(node.find('input').first()).toHaveValue(10);
  expect(node.find('input').at(1)).toHaveValue(200);
});

it('should call the confirm method with the new target values', async () => {
  const spy = jest.fn();
  const node = mount(<TargetValueModal {...validProps} onConfirm={spy} />);

  await node.setProps({open: true});
  await node.setState({target: 123, baseline: 0});

  node.find('button[type="primary"]').simulate('click');

  expect(spy).toHaveBeenCalledWith({target: 123, baseline: 0});
});

it('should disable the confirm button if an input field does not have a valid value', async () => {
  const node = mount(<TargetValueModal {...validProps} />);

  await node.setProps({open: true});
  await node.setState({target: '123.45.6', baseline: 0});

  expect(node.find('button[type="primary"]')).toBeDisabled();
});

it('should disable the confirm button if the target is below the baseline', async () => {
  const node = mount(<TargetValueModal {...validProps} />);

  await node.setProps({open: true});
  await node.setState({target: '10', baseline: '100'});

  expect(node.find('button[type="primary"]')).toBeDisabled();
});

it('should show error messages', async () => {
  const node = mount(<TargetValueModal {...validProps} />);

  await node.setProps({open: true});
  await node.setState({target: '123.45.6', baseline: 0});

  expect(node).toIncludeText('Must be a non-negative number');

  await node.setState({target: '10', baseline: '100'});

  expect(node).toIncludeText('Target must be greater than baseline');
});

it('should render duration inputs when its a duration', async () => {
  const node = mount(
    <TargetValueModal
      reportResult={{
        data: {
          processDefinitionKey: 'a',
          processDefinitionVersion: 1,
          view: {
            entity: 'processInstance',
            operation: 'avg',
            property: 'duration'
          },
          groupBy: {
            type: 'none'
          },
          visualization: 'number'
        },
        result: 12
      }}
      configuration={{
        targetValue: {
          active: false
        }
      }}
    />
  );

  await node.setProps({open: true});

  expect(node.find('select')).toBePresent();
});
