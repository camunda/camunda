import React from 'react';
import {shallow} from 'enzyme';
import {Button, Input} from 'components';

import ChartTargetInput from './ChartTargetInput';

const validProps = {
  report: {
    combined: false,
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
      values: {dateFormat: 'seconds', target: 50, isBelow: false}
    }
  }
};

const sampleTargetValue = {
  active: true,
  values: {
    isBelow: true,
    target: 15,
    dateFormat: ''
  }
};

it('should render without crashing', () => {
  shallow(<ChartTargetInput {...validProps} />);
});

it('should not crash when target value values are not defined', () => {
  shallow(<ChartTargetInput {...{...validProps, configuration: {targetValue: {active: false}}}} />);
});

it('should add is-active classname to the clicked button in the buttonGroup', () => {
  const node = shallow(
    <ChartTargetInput
      report={validProps}
      onChange={() => {}}
      configuration={validProps.configuration}
    />
  );

  node
    .find(Button)
    .first()
    .simulate('click');

  expect(node.find(Button).first()).toHaveClassName('is-active');
});

it('should display the current target values target', () => {
  const node = shallow(<ChartTargetInput {...validProps} />);
  node.setProps({configuration: {targetValue: sampleTargetValue}});

  expect(node.find(Input).first()).toHaveValue(15);
});

it('should display select dateFormat dropdown when viewProberty equal duration', () => {
  const node = shallow(<ChartTargetInput {...validProps} />);

  expect(node.find('Select')).toBePresent();
});

it('should hide select dateFormat dropdown when viewProberty is not equal duration', () => {
  const newProps = {
    report: {
      combined: false,
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
        values: {dateFormat: 'seconds', target: 50, isBelow: false}
      }
    }
  };
  const node = shallow(<ChartTargetInput {...newProps} />);
  expect(node.find('Select')).not.toBePresent();
});

it('should invoke the onChange prop on button click', async () => {
  const spy = jest.fn();
  const node = shallow(<ChartTargetInput {...validProps} onChange={spy} />);

  node
    .find(Button)
    .first()
    .simulate('click');

  expect(spy).toHaveBeenCalledWith('targetValue', {
    active: true,
    values: {dateFormat: 'seconds', isBelow: false, target: 50}
  });
});

it('should display select date format if combined report is duration report', async () => {
  const combinedProps = {
    ...validProps,
    report: {
      ...validProps.reportResult,
      combined: true,
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
  const node = shallow(<ChartTargetInput {...combinedProps} />);

  expect(node.find('Select')).toBePresent();
});

// snapshot
it('should include an error message when invalid target value is typed', () => {
  const node = shallow(<ChartTargetInput {...validProps} />);
  node.setProps({
    configuration: {
      targetValue: {active: true, values: {...sampleTargetValue.values, target: 'e'}}
    }
  });

  expect(node).toMatchSnapshot();
});
