/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {Button, Input, Message} from 'components';

import ChartTargetInput from './ChartTargetInput';

const validProps = {
  report: {
    combined: false,
    data: {
      processDefinitionKey: 'a',
      processDefinitionVersion: 1,
      view: {
        entity: 'flowNode',
        properties: ['duration'],
      },
      visualization: 'bar',
      configuration: {
        targetValue: {
          active: true,
          durationChart: {
            value: '2',
            unit: 'hours',
            isBelow: false,
          },
        },
      },
    },
  },
  onChange: () => {},
};

const sampleTargetValue = {
  active: true,
  durationChart: {
    isBelow: true,
    value: '15',
    dateFormat: 'months',
  },
};

it('should render without crashing', () => {
  shallow(<ChartTargetInput {...validProps} />);
});

it('should add isActive classname to the clicked button in the buttonGroup', () => {
  const node = shallow(<ChartTargetInput {...validProps} />);

  node.find(Button).first().simulate('click');

  expect(node.find(Button).first().props().active).toBe(true);
});

it('should display the current target values target', () => {
  const node = shallow(<ChartTargetInput {...validProps} />);
  node.setProps({
    report: {
      ...validProps.report,
      data: {...validProps.report.data, configuration: {targetValue: sampleTargetValue}},
    },
  });

  expect(node.find(Input).first()).toHaveValue('15');
});

it('should display select dateFormat dropdown when viewProperty equal duration', () => {
  const node = shallow(<ChartTargetInput {...validProps} />);

  expect(node.find('Select')).toExist();
});

it('should hide select dateFormat dropdown when viewProperty is not equal duration', () => {
  const newProps = {
    report: {
      combined: false,
      data: {
        processDefinitionKey: 'a',
        processDefinitionVersion: 1,
        view: {
          entity: 'flowNode',
          properties: ['frequency'],
        },
        visualization: 'bar',
        configuration: {
          targetValue: {
            active: true,
            countChart: {value: '50', isBelow: false},
          },
        },
      },
    },
  };
  const node = shallow(<ChartTargetInput {...newProps} />);
  expect(node.find('Select')).not.toExist();
});

it('should invoke the onChange prop on button click', async () => {
  const spy = jest.fn();
  const node = shallow(<ChartTargetInput {...validProps} onChange={spy} />);

  node.find(Button).first().simulate('click');

  expect(spy).toHaveBeenCalledWith({targetValue: {durationChart: {isBelow: {$set: false}}}});
});

it('should display select date format if combined report is duration report', async () => {
  const combinedProps = {
    ...validProps,
    report: {
      ...validProps.report,
      combined: true,
      result: {
        data: {
          test: {
            data: {
              visualization: 'bar',
              view: {
                properties: ['duration'],
              },
            },
          },
        },
      },
    },
  };
  const node = shallow(<ChartTargetInput {...combinedProps} />);

  expect(node.find('Select')).toExist();
});

it('should include an error message when invalid target value is typed', () => {
  const node = shallow(<ChartTargetInput {...validProps} />);
  node.setProps({
    report: {
      ...validProps.report,
      data: {
        ...validProps.report.data,
        configuration: {
          targetValue: {
            active: true,
            durationChart: {...sampleTargetValue.durationChart, value: 'e'},
          },
        },
      },
    },
  });

  expect(node.find(Message)).toExist();
});
