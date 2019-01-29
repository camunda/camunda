import React from 'react';
import {shallow} from 'enzyme';

import {Dropdown} from 'components';

import DecisionControlPanel from './DecisionControlPanel';
import {reportConfig} from 'services';

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    reportConfig: {
      getLabelFor: () => 'foo',
      view: {foo: {data: 'foo', label: 'viewfoo'}},
      groupBy: {
        foo: {data: 'foo', label: 'groupbyfoo'},
        variable: {data: {value: []}, label: 'Variables'}
      },
      visualization: {foo: {data: 'foo', label: 'visualizationfoo'}},
      isAllowed: jest.fn().mockReturnValue(true),
      getNext: jest.fn()
    }
  };
});

const data = {
  decisionDefinitionKey: 'aKey',
  decisionDefinitionVersion: 'aVersion',
  view: {operation: 'rawData'},
  groupBy: {type: 'none', unit: null},
  visualization: 'table',
  filter: null,
  configuration: {xml: 'fooXml'}
};

it('should call the provided updateReport property function when a setting changes', () => {
  const spy = jest.fn();
  const node = shallow(<DecisionControlPanel {...data} updateReport={spy} />);

  node
    .find(Dropdown.Option)
    .at(0)
    .simulate('click');

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].view).toEqual({$set: {operation: 'rawData'}});
});

it('should disable the groupBy and visualization Selects if view is not selected', () => {
  const node = shallow(<DecisionControlPanel {...data} view="" />);

  expect(node.find('.configDropdown').at(1)).toBeDisabled();
  expect(node.find('.configDropdown').at(2)).toBeDisabled();
});

it('should not disable the groupBy and visualization Selects if view is selected', () => {
  const node = shallow(<DecisionControlPanel {...data} />);

  expect(node.find('.configDropdown').at(1)).not.toBeDisabled();
  expect(node.find('.configDropdown').at(2)).not.toBeDisabled();
});

it('should set or reset following selects according to the getNext function', () => {
  const spy = jest.fn();
  const node = shallow(<DecisionControlPanel {...data} updateReport={spy} />);

  reportConfig.getNext.mockReturnValueOnce('next');

  node
    .find(Dropdown.Option)
    .at(0)
    .simulate('click');

  expect(spy.mock.calls[0][0].groupBy).toEqual({$set: 'next'});
});
