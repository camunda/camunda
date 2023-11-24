/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import {RadioButton, NumberInput} from '@carbon/react';
import {ComponentProps} from 'react';

import ChartTargetInput from './ChartTargetInput';

const validProps = {
  report: {
    combined: false,
    data: {
      view: {
        properties: ['duration'],
      },
      configuration: {
        targetValue: {
          durationChart: {
            value: '2',
            unit: 'hours',
            isBelow: false,
          },
        },
      },
    },
    result: {},
  },
  onChange: jest.fn(),
} as unknown as ComponentProps<typeof ChartTargetInput>;

const sampleTargetValue = {
  durationChart: {
    isBelow: true,
    value: '15',
    dateFormat: 'months',
  },
};

it('should render without crashing', () => {
  shallow(<ChartTargetInput {...validProps} />);
});

it('should set checked prop to true for the clicked radio button', () => {
  const node = shallow(<ChartTargetInput {...validProps} />);

  node.find(RadioButton).first().simulate('click');

  expect(node.find(RadioButton).first().prop('checked')).toBe(true);
});

it('should display the current target values target', () => {
  const node = shallow(<ChartTargetInput {...validProps} />);
  node.setProps({
    report: {
      ...validProps.report,
      data: {...validProps.report.data, configuration: {targetValue: sampleTargetValue}},
    },
  });

  expect(node.find(NumberInput).first()).toHaveValue('15');
});

it('should display select dateFormat dropdown when viewProperty equal duration', () => {
  const node = shallow(<ChartTargetInput {...validProps} />);

  expect(node.find('CarbonSelect')).toExist();
});

it('should hide select dateFormat dropdown when viewProperty is not equal duration', () => {
  const newProps = {
    report: {
      combined: false,
      data: {
        view: {
          properties: ['frequency'],
        },
        configuration: {
          targetValue: {},
        },
      },
      result: {},
    },
    onChange: jest.fn(),
  } as unknown as ComponentProps<typeof ChartTargetInput>;
  const node = shallow(<ChartTargetInput {...newProps} />);
  expect(node.find('CarbonSelect')).not.toExist();
});

it('should invoke the onChange prop on button click', async () => {
  const spy = jest.fn();
  const node = shallow(<ChartTargetInput {...validProps} onChange={spy} />);

  node.find(RadioButton).first().simulate('click');

  expect(spy).toHaveBeenCalledWith({targetValue: {durationChart: {isBelow: {$set: false}}}});
});

it('should display select date format if combined report is duration report', async () => {
  const combinedProps = {
    ...validProps,
    report: {
      ...validProps.report,
      combined: true,
      data: {
        view: {
          properties: ['duration'],
        },
        configuration: {targetValue: {test: {value: 0, isBelow: true, unit: 'seconds'}}},
      },
      result: {
        data: {
          test: {
            data: {
              view: {
                properties: ['duration'],
              },
            },
          },
        },
      },
    },
  } as unknown as ComponentProps<typeof ChartTargetInput>;
  const node = shallow(<ChartTargetInput {...combinedProps} />);

  expect(node.find('CarbonSelect')).toExist();
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

  expect(node.find(NumberInput).prop('invalid')).toBe(true);
  expect(node.find(NumberInput).prop('invalidText')).toBe('Enter a positive number');
});
