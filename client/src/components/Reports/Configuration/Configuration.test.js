import React from 'react';
import {shallow} from 'enzyme';

import Configuration from './Configuration';
import {typeA, typeB, typeC} from './visualizations';
import {Button} from 'components';

jest.mock('./visualizations', () => {
  const typeA = () => null;
  typeA.defaults = {
    propA: 'abc',
    propB: 1
  };
  typeA.onUpdate = jest.fn().mockReturnValue({prop: 'updateValue'});

  const typeB = () => null;
  typeB.defaults = {
    propC: false
  };

  const typeC = () => null;
  typeC.defaults = jest.fn().mockReturnValue({propD: 20});

  return {typeA, typeB, typeC, bar: typeA, line: typeA};
});

it('should be disabled if no type is set', () => {
  const node = shallow(<Configuration report={{}} />);

  expect(node.find('Popover')).toBeDisabled();
});

it('should be disabled if combined report is empty', () => {
  const node = shallow(
    <Configuration type="combined" report={{combined: true, data: {reportIds: null}}} />
  );

  expect(node.find('Popover')).toBeDisabled();
});

it('should contain the Component from the visualizations based on the type', () => {
  const node = shallow(<Configuration report={{}} type="typeA" onChange={() => {}} />);

  expect(node.find(typeA)).toBePresent();

  node.setProps({type: 'typeB'});

  expect(node.find(typeA)).not.toBePresent();
  expect(node.find(typeB)).toBePresent();
});

it('should reset to defaults based on the defaults provided by the visualization', () => {
  const spy = jest.fn();
  const node = shallow(
    <Configuration report={{}} type="typeA" onChange={spy} configuration={{}} />
  );

  node.find(Button).simulate('click');

  expect(spy).toHaveBeenCalledWith({configuration: {propA: 'abc', propB: 1}});
});

it('should call a potential component defaults method to determine defaults', () => {
  const spy = jest.fn();
  const node = shallow(
    <Configuration report={{}} type="typeC" onChange={spy} configuration={{}} />
  );

  node.find(Button).simulate('click');

  expect(typeC.defaults).toHaveBeenCalledWith(node.instance().props);
  expect(spy).toHaveBeenCalledWith({configuration: {propD: 20}});
});

it('should call the onUpdate method of the component and propagate changes', () => {
  const spy = jest.fn();
  const node = shallow(
    <Configuration report={{}} type="typeA" onChange={spy} configuration={{}} />
  );

  node.setProps({report: 'some new report'});

  expect(typeA.onUpdate).toHaveBeenCalled();
  expect(spy).toHaveBeenCalledWith({configuration: {prop: 'updateValue'}});
});
