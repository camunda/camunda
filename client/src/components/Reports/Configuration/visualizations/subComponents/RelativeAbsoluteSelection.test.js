import React from 'react';
import {shallow} from 'enzyme';

import RelativeAbsoluteSelection from './RelativeAbsoluteSelection';

xit('should match snapshot', () => {
  const node = shallow(<RelativeAbsoluteSelection configuration={{}} />);

  expect(node).toMatchSnapshot();
});

xit('should call the onChange method with the correct prop and value', () => {
  const spy = jest.fn();
  const node = shallow(<RelativeAbsoluteSelection configuration={{}} onChange={spy} />);

  node
    .find('Switch')
    .at(0)
    .simulate('change', {target: {checked: false}});

  expect(spy).toHaveBeenCalledWith('hideAbsoluteValue', true);
});

xit('disable the reltive selection when relativeDisabled is true', () => {
  const node = shallow(
    <RelativeAbsoluteSelection
      relativeDisabled={true}
      configuration={{
        hideAbsoluteValue: false,
        hideRelativeValue: false
      }}
      onchange={() => {}}
    />
  );

  const switchProps = node
    .find('Switch')
    .at(1)
    .props();

  expect(switchProps.disabled).toBe(true);
  expect(switchProps.title).toMatch('Relative values are only possible');
});
