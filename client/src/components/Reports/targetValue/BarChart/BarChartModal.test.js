import React from 'react';
import {mount} from 'enzyme';

import BarChartModal from './BarChartModal';

jest.mock('components', () => {
  const LabeledInput = ({children, isInvalid, ...props}) => (
    <div>
      <input {...props} />
      {children}
    </div>
  );

  const ErrorMessage = function ErrorMessage(props) {
    return <div {...props}>{props.children}</div>;
  };

  const Select = props => <select {...props}>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  const Modal = ({onEnterPress, ...props}) => <div {...props}>{props.children}</div>;
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
    LabeledInput,
    ErrorMessage,
    Select,
    Modal,
    Button,
    ButtonGroup: props => <div {...props}>{props.children}</div>
  };
});

const validProps = {
  reportResult: {
    data: {
      processDefinitionKey: 'a',
      processDefinitionVersion: 1,
      view: {
        entity: 'flowNode',
        operation: 'avg_flowNode_duration',
        property: 'duration'
      },
      visualization: 'bar'
    }
  },
  configuration: {
    targetValue: {
      active: true,
      values: {type: 'seconds', target: 50, isBelow: false}
    }
  }
};

it('should render without crashing', () => {
  mount(<BarChartModal {...validProps} />);
});

it('should add is-active classname to the clicked button in the buttonGroup', () => {
  const node = mount(<BarChartModal {...validProps} />);
  node
    .find('ButtonGroup button')
    .first()
    .simulate('click');
  expect(node.find('ButtonGroup button').first()).toHaveClassName('is-active');
});

it('should display the current target values target', () => {
  const node = mount(<BarChartModal {...validProps} />);
  node.setState({target: 10});

  expect(node.find('input').first()).toHaveValue(10);
});

it('should display select dateFormat dropdown when viewProberty equal duration', () => {
  const node = mount(<BarChartModal {...validProps} />);
  expect(node).toIncludeText('Milliseconds');
});

it('should hide select dateFormat dropdown when viewProberty is not equal duration', () => {
  const newProps = {
    reportResult: {
      data: {
        processDefinitionKey: 'a',
        processDefinitionVersion: 1,
        view: {
          entity: 'flowNode',
          operation: 'something_else',
          property: 'something_else'
        },
        visualization: 'bar'
      }
    },
    configuration: {
      targetValue: {
        active: true,
        values: {type: 'seconds', target: 50, isBelow: false}
      }
    }
  };
  const node = mount(<BarChartModal {...newProps} />);
  expect(node).not.toIncludeText('Milliseconds');
});

it('should show an error message when invalid target value is typed', () => {
  const node = mount(<BarChartModal {...validProps} />);
  node.setState({target: 'wrong value'});

  expect(node).toIncludeText('Must be a non-negative number');
});

it('should disable the confirm button when invalid value is typed into the field', () => {
  const node = mount(<BarChartModal {...validProps} />);
  node.setState({target: '-1'});
  expect(node.find('button[type="primary"]')).toBeDisabled();
});

it('should invoke the confirm prop on confirm button click', async () => {
  const spy = jest.fn();
  const node = mount(<BarChartModal {...validProps} onConfirm={spy} />);

  await node.setProps({open: true});

  node.find('button[type="primary"]').simulate('click');

  expect(spy).toHaveBeenCalled();
});
