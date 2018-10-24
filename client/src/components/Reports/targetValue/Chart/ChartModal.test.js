import React from 'react';
import {mount} from 'enzyme';

import ChartModal from './ChartModal';

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
    reportType: 'single',
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
  mount(<ChartModal {...validProps} />);
});

it('should add is-active classname to the clicked button in the buttonGroup', () => {
  const node = mount(<ChartModal {...validProps} />);
  node
    .find('ButtonGroup button')
    .first()
    .simulate('click');
  expect(node.find('ButtonGroup button').first()).toHaveClassName('is-active');
});

it('should display the current target values target', () => {
  const node = mount(<ChartModal {...validProps} />);
  node.setState({target: 10});

  expect(node.find('input').first()).toHaveValue(10);
});

it('should display select dateFormat dropdown when viewProberty equal duration', () => {
  const node = mount(<ChartModal {...validProps} />);
  expect(node).toIncludeText('Milliseconds');
});

it('should hide select dateFormat dropdown when viewProberty is not equal duration', () => {
  const newProps = {
    reportResult: {
      reportType: 'single',
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
  const node = mount(<ChartModal {...newProps} />);
  expect(node).not.toIncludeText('Milliseconds');
});

it('should show an error message when invalid target value is typed', () => {
  const node = mount(<ChartModal {...validProps} />);
  node.setState({target: 'wrong value'});

  expect(node).toIncludeText('Must be a non-negative number');
});

it('should disable the confirm button when invalid value is typed into the field', () => {
  const node = mount(<ChartModal {...validProps} />);
  node.setState({target: '-1'});
  expect(node.find('button[type="primary"]')).toBeDisabled();
});

it('should invoke the confirm prop on confirm button click', async () => {
  const spy = jest.fn();
  const node = mount(<ChartModal {...validProps} onConfirm={spy} />);

  await node.setProps({open: true});

  node.find('button[type="primary"]').simulate('click');

  expect(spy).toHaveBeenCalled();
});

describe('isDurationReport', () => {
  it('should return true if combined report is duration report', async () => {
    const combinedProps = {
      ...validProps,
      reportResult: {
        ...validProps.reportResult,
        reportType: 'combined',
        result: {
          test: {
            data: {
              visualization: 'bar',
              view: {
                property: 'duration'
              }
            }
          }
        }
      }
    };
    const node = mount(<ChartModal {...combinedProps} />);

    expect(node.instance().isDurationReport()).toBe(true);
  });

  it('should return true if single report is bar or line', async () => {
    const node = mount(<ChartModal {...validProps} />);

    expect(node.instance().isDurationReport()).toBe(true);
  });

  it('should return false if single report is not duartion report', async () => {
    const newProps = {
      ...validProps,
      reportResult: {
        ...validProps.reportResult,
        data: {
          processDefinitionKey: 'a',
          processDefinitionVersion: 1,
          view: {
            entity: 'flowNode',
            operation: 'avg_flowNode_duration',
            property: 'not duartion'
          },
          visualization: 'bar'
        }
      }
    };
    const node = mount(<ChartModal {...newProps} />);

    expect(node.instance().isDurationReport()).toBe(false);
  });
});
