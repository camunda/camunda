import React from 'react';
import {mount} from 'enzyme';

import ThresholdInput from './ThresholdInput';

jest.mock('components', () => {
  const Select = props => <select {...props}>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {
    Input: ({value, onChange}) => <input value={value} onChange={onChange} />,
    Select
  };
});

it('should contain a single input field if the type is not duration', () => {
  const node = mount(<ThresholdInput type="number" value="123" />);

  expect(node.find('input')).toBePresent();
  expect(node.find('select')).not.toBePresent();
});

it('should contain a input and a select field if the type is duration', () => {
  const node = mount(<ThresholdInput type="duration" value={{value: '123', unit: 'minutes'}} />);

  expect(node.find('input')).toBePresent();
  expect(node.find('select')).toBePresent();
});

it('should call the change handler when changing the value', () => {
  const spy = jest.fn();
  const node = mount(
    <ThresholdInput onChange={spy} type="duration" value={{value: '123', unit: 'minutes'}} />
  );

  node.find('input').simulate('change', {target: {value: '1234'}});

  expect(spy).toHaveBeenCalledWith({value: '1234', unit: 'minutes'});
});
