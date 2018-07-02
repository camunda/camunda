import React from 'react';
import {mount} from 'enzyme';

import NumberInputs from './NumberInputs';

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

  return {
    LabeledInput,
    ErrorMessage
  };
});

const validProps = {
  configuration: {
    targetValue: {
      active: false,
      values: {
        baseline: 10,
        target: 200
      }
    }
  },
  data: {},
  setData: jest.fn(),
  setValid: jest.fn()
};

it('should render without crashing', () => {
  mount(<NumberInputs {...validProps} />);
});

it('should display the current target values', async () => {
  const node = mount(<NumberInputs {...validProps} />);

  node.setProps({data: {baseline: 10, target: 200}});

  expect(node.find('input').first()).toHaveValue(10);
  expect(node.find('input').at(1)).toHaveValue(200);
});

it('should update target values', () => {
  const spy = jest.fn();
  const node = mount(<NumberInputs {...validProps} setData={spy} setValid={jest.fn()} />);

  node.setProps({data: {baseline: 10, target: 200}});
  node.instance().change('target')({target: {value: '73'}});

  expect(spy).toHaveBeenCalledWith({
    baseline: 10,
    target: '73'
  });
});

it('should set invalid state if an input field does not have a valid value', async () => {
  const spy = jest.fn();
  const node = mount(<NumberInputs {...validProps} setValid={spy} setData={jest.fn()} />);

  node.instance().change('target')({target: {value: 'notAValidValue'}});

  expect(spy).toHaveBeenCalledWith(false);
});

it('should show error messages', async () => {
  const node = mount(<NumberInputs {...validProps} />);

  await node.setProps({data: {target: 1, baseline: 'whatever'}});

  expect(node).toIncludeText('Must be a non-negative number');
});
