/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {RadioButton, NumberInput} from '@carbon/react';
import {ComponentProps} from 'react';

import ChartTargetInput from './ChartTargetInput';

const validProps = {
  report: {
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

  expect(node.find('Select')).toExist();
});

it('should hide select dateFormat dropdown when viewProperty is not equal duration', () => {
  const newProps = {
    report: {
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
  expect(node.find('Select')).not.toExist();
});

it('should invoke the onChange prop on button click', async () => {
  const spy = jest.fn();
  const node = shallow(<ChartTargetInput {...validProps} onChange={spy} />);

  node.find(RadioButton).first().simulate('click');

  expect(spy).toHaveBeenCalledWith({targetValue: {durationChart: {isBelow: {$set: false}}}});
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
