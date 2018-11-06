import React from 'react';
import {shallow} from 'enzyme';

import Configuration from './Configuration';
import {typeA, typeB} from './visualizations';
import {Button} from 'components';

jest.mock('./visualizations', () => {
  const typeA = () => null;
  typeA.defaults = {
    propA: 'abc',
    propB: 1
  };

  const typeB = () => null;
  typeB.defaults = {
    propC: false
  };

  return {typeA, typeB};
});

it('should be disabled if no type is set', () => {
  const node = shallow(<Configuration />);

  expect(node.find('Popover')).toBeDisabled();
});

it('should contain the Component from the visualizations based on the type', () => {
  const node = shallow(<Configuration type="typeA" />);

  expect(node.find(typeA)).toBePresent();

  node.setProps({type: 'typeB'});

  expect(node.find(typeA)).not.toBePresent();
  expect(node.find(typeB)).toBePresent();
});

it('should reset to defaults based on the defaults provided by the visualization', () => {
  const innerSpy = jest.fn();
  const spy = jest.fn().mockReturnValue(innerSpy);
  const node = shallow(<Configuration type="typeA" onChange={spy} />);

  node.find(Button).simulate('click');

  expect(spy).toHaveBeenCalledWith('propA');
  expect(innerSpy).toHaveBeenCalledWith('abc');

  expect(spy).toHaveBeenCalledWith('propB');
  expect(innerSpy).toHaveBeenCalledWith(1);
});
