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

jest.mock('components', () => {
  const Modal = props => <div {...props}>{props.children}</div>;
  Modal.Header = props => <div>{props.children}</div>;
  Modal.Content = props => <div>{props.children}</div>;
  Modal.Actions = props => <div>{props.children}</div>;

  const Select = props => <select {...props}>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {
    Modal,
    Select,
    Button: props => (
      <button {...props} active="true">
        {props.children}
      </button>
    ),
    BPMNDiagram: () => <div>BPMNDiagram</div>,
    TargetValueBadge: () => <div>TargetValueBadge</div>,
    Input: props => (
      <input
        ref={props.reference}
        id={props.id}
        readOnly={props.readOnly}
        type={props.type}
        onChange={props.onChange}
        value={props.value}
        className={props.className}
      />
    ),
    Table: ({body}) => <div>{JSON.stringify(body.map(row => [row[0], row[1]]))}</div>
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
