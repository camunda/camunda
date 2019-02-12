import React from 'react';
import {shallow} from 'enzyme';

import {Dropdown} from 'components';

import DecisionControlPanel from './DecisionControlPanel';

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    decisionConfig: {
      getLabelFor: () => 'foo',
      options: {
        view: {foo: {data: 'foo', label: 'viewfoo'}},
        groupBy: {
          foo: {data: 'foo', label: 'groupbyfoo'},
          inputVariable: {data: {value: []}, label: 'Input Variable'}
        },
        visualization: {foo: {data: 'foo', label: 'visualizationfoo'}}
      },
      isAllowed: jest.fn().mockReturnValue(true),
      getNext: jest.fn(),
      update: jest.fn()
    }
  };
});

const report = {
  data: {
    decisionDefinitionKey: 'aKey',
    decisionDefinitionVersion: 'aVersion',
    view: {operation: 'rawData'},
    groupBy: {type: 'none', unit: null},
    visualization: 'table',
    filter: null,
    configuration: {
      xml:
        '<decision id="aKey"><input id="anId" label="aName" /><input id="anotherId" label="anotherName" /></decision>'
    }
  }
};

it('should call the provided updateReport property function when a setting changes', () => {
  const spy = jest.fn();
  const node = shallow(<DecisionControlPanel report={report} updateReport={spy} />);

  node
    .find(Dropdown.Option)
    .at(0)
    .simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should disable the groupBy and visualization Selects if view is not selected', () => {
  const node = shallow(
    <DecisionControlPanel report={{...report, data: {...report.data, view: ''}}} />
  );

  expect(node.find('.configDropdown').at(1)).toBeDisabled();
  expect(node.find('.configDropdown').at(2)).toBeDisabled();
});

it('should not disable the groupBy and visualization Selects if view is selected', () => {
  const node = shallow(<DecisionControlPanel report={report} />);

  expect(node.find('.configDropdown').at(1)).not.toBeDisabled();
  expect(node.find('.configDropdown').at(2)).not.toBeDisabled();
});

it('should include variables in the groupby options', () => {
  const node = shallow(<DecisionControlPanel report={report} />);

  const varDropdown = node.find('[label="Group by"] Submenu DropdownOption');

  expect(varDropdown.at(0).prop('children')).toBe('aName');
  expect(varDropdown.at(1).prop('children')).toBe('anotherName');
});
