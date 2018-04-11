import React from 'react';
import {mount} from 'enzyme';

import TargetValueModal from './TargetValueModal';

jest.mock('bpmn-js/lib/NavigatedViewer', () => {
  return class Viewer {
    constructor() {
      this.elements = [
        {id: 'a', name: 'Element A'},
        {id: 'b', name: 'Element B'},
        {id: 'c', name: 'Element C'}
      ];

      this.elementRegistry = {
        filter: () => {
          return {
            map: () => this.elements
          };
        }
      };
    }
    attachTo = jest.fn();
    importXML = jest.fn((xml, cb) => cb());
    get = () => {
      return this.elementRegistry;
    };
  };
});

jest.mock('services', () => {
  return {
    formatters: {
      duration: a => a
    }
  };
});

const validProps = {
  reportResult: {
    data: {
      processDefinitionKey: 'a',
      processDefinitionVersion: 1,
      view: {
        entity: 'flowNode',
        operation: 'avg',
        property: 'duration'
      },
      groupBy: {
        type: 'flowNode'
      },
      visualization: 'heat'
    },
    result: {}
  },
  configuration: {
    targetValue: {
      active: false,
      values: {
        a: {
          value: 12,
          unit: 'days'
        }
      }
    }
  }
};

it('should display the bpmn diagram in the modal', () => {
  const node = mount(<TargetValueModal {...validProps} />);

  expect(node).toIncludeText('BPMNDiagram');
});

it('should display a list of flow nodes in a table', async () => {
  const node = mount(<TargetValueModal {...validProps} />);

  await node.setProps({open: true});

  expect(node).toIncludeText('Element A');
  expect(node).toIncludeText('Element B');
  expect(node).toIncludeText('Element C');
});

it('should save the changes target values', async () => {
  const spy = jest.fn();

  const node = mount(<TargetValueModal {...validProps} onConfirm={spy} />);

  await node.setProps({open: true});

  node.instance().setTarget('value', 'a')({target: {value: '34'}});
  node.instance().setTarget('unit', 'a')({target: {value: 'years'}});

  spy.mockClear();

  node.find('button[type="primary"]').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    a: {
      unit: 'years',
      value: 34
    }
  });
});

it('should apply previously defined target values to input fields', async () => {
  const node = mount(<TargetValueModal {...validProps} />);

  await node.setProps({open: true});

  expect(node.state('values').a.value).toBe('12');
  expect(node.state('values').a.unit).toBe('days');
});

it('should set isInvalid property for input if value is invalid', async () => {
  const node = mount(<TargetValueModal {...validProps} />);
  await node.setProps({open: true});

  node.instance().setTarget('value', 'a')({target: {value: 'invalid'}});

  await node.update();

  expect(
    node
      .find('.TargetValueModal__selection--input')
      .first()
      .props()
  ).toHaveProperty('isInvalid', true);
});
