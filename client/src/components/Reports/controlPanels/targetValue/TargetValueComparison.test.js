/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import TargetValueComparison from './TargetValueComparison';

const validProps = {
  report: {
    data: {
      processDefinitionKey: 'a',
      processDefinitionVersion: 1,
      view: {
        entity: 'flowNode',
        properties: ['duration'],
      },
      groupBy: {
        type: 'flowNodes',
      },
      visualization: 'heat',
      configuration: {
        heatmapTargetValue: {
          active: false,
          values: {
            a: {
              value: 12,
              unit: 'days',
            },
          },
        },
      },
    },
    result: {data: {}},
  },
};

const validPropsWithoutTargetValues = {
  report: {
    ...validProps.report,
    data: {
      ...validProps.data,
      configuration: {
        heatmapTargetValue: {
          active: false,
          values: {},
        },
      },
    },
  },
};

const invalidProps = {
  report: {
    data: {
      processDefinitionKey: 'a',
      processDefinitionVersion: 1,
      view: {
        entity: 'flowNode',
        properties: ['duration'],
      },
      groupBy: {
        type: 'None',
      },
      visualization: 'heat',
      configuration: {
        active: false,
        heatmapTargetValue: {
          a: {
            value: 12,
            unit: 'days',
          },
        },
      },
    },
    result: {data: {}},
  },
};

it('should display a double button', () => {
  const node = shallow(<TargetValueComparison {...invalidProps} />);

  expect(node.find('.toggleButton')).toExist();
  expect(node.find('.editButton')).toExist();
});

it('should not render the modal if result is not yet available', () => {
  const node = shallow(
    <TargetValueComparison report={{...validProps.report, result: undefined}} />
  );

  expect(node.find('DurationHeatmapModal')).not.toExist();
});

it('should toggle the mode with the left button', () => {
  const spy = jest.fn();
  const node = shallow(<TargetValueComparison {...validProps} onChange={spy} />);

  node.find('.toggleButton').simulate('click');

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].configuration.heatmapTargetValue.active).toEqual({$set: true});
});

it('should open the modal with the left button if there are no target values set', async () => {
  const node = shallow(<TargetValueComparison {...validPropsWithoutTargetValues} />);

  await node.find('.toggleButton').simulate('click');

  expect(node.state('modalOpen')).toBe(true);
});

it('should open the target value edit modal on with the right button', async () => {
  const node = shallow(<TargetValueComparison {...validProps} />);

  await node.find('.editButton').simulate('click');

  expect(node.state('modalOpen')).toBe(true);
});

it('it should toggle target value view mode off if no target values are defined', async () => {
  const spy = jest.fn();
  const node = shallow(<TargetValueComparison {...validProps} onChange={spy} />);

  node.instance().confirmModal({});

  expect(spy).toHaveBeenCalledWith({
    configuration: {
      heatmapTargetValue: {
        $set: {
          active: false,
          values: {},
        },
      },
    },
  });
});
