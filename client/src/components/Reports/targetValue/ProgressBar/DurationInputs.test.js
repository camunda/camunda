import React from 'react';
import {mount} from 'enzyme';

import DurationInputs from './DurationInputs';

jest.mock('services', () => {
  return {
    formatters: {
      convertDurationToSingleNumber: () => 123
    },
    isDurationValue: data => typeof data !== 'number'
  };
});

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

  return {
    LabeledInput,
    ErrorMessage,
    Select
  };
});

const validProps = {
  configuration: {
    targetValue: {
      active: false,
      values: {
        baseline: {value: '12', unit: 'weeks'},
        target: {value: '15', unit: 'months'}
      }
    }
  },
  data: {},
  setData: jest.fn(),
  setValid: jest.fn()
};

it('should render without crashing', () => {
  mount(<DurationInputs {...validProps} />);
});

it('should display the current target values', async () => {
  const node = await mount(<DurationInputs {...validProps} />);

  node.setProps({
    data: {
      baseline: {value: '12', unit: 'weeks'},
      target: {value: '15', unit: 'months'}
    }
  });

  expect(node.find('input').first()).toHaveValue('12');
  expect(node.find('input').at(1)).toHaveValue('15');
});

it('should update target values', () => {
  const spy = jest.fn();
  const node = mount(<DurationInputs {...validProps} setData={spy} setValid={jest.fn()} />);

  node.setProps({
    data: {
      baseline: {value: '12', unit: 'weeks'},
      target: {value: '15', unit: 'months'}
    }
  });
  node.instance().change('target', 'value')({target: {value: '73'}});

  expect(spy).toHaveBeenCalledWith({
    baseline: {value: '12', unit: 'weeks'},
    target: {value: '73', unit: 'months'}
  });
});

it('should set invalid state if an input field does not have a valid value', async () => {
  const spy = jest.fn();
  const node = mount(<DurationInputs {...validProps} setValid={spy} setData={jest.fn()} />);

  node.instance().change('target', 'value')({target: {value: 'notAValidValue'}});

  expect(spy).toHaveBeenCalledWith(false);
});

it('should show error messages', async () => {
  const node = await mount(<DurationInputs {...validProps} />);

  await node.setProps({
    data: {
      baseline: {value: '12gdsf', unit: 'weeks'},
      target: {value: '15', unit: 'months'}
    }
  });

  expect(node).toIncludeText('Must be a non-negative number');
});
