import React from 'react';
import {mount} from 'enzyme';

import DurationHeatmapModal from './DurationHeatmapModal';

console.error = jest.fn();

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
  const Modal = ({onConfirm, ...props}) => <div {...props}>{props.children}</div>;
  Modal.Header = props => <div>{props.children}</div>;
  Modal.Content = props => <div>{props.children}</div>;
  Modal.Actions = props => <div>{props.children}</div>;

  const Select = props => <select {...props}>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {
    Button: props => (
      <button {...props} active={props.active ? 'true' : undefined}>
        {props.children}
      </button>
    ),
    Modal,
    BPMNDiagram: () => <div>BPMNDiagram</div>,
    Table: ({body}) => (
      <div>{body.map(row => row.map((col, idx) => <div key={idx}>{col}</div>))}</div>
    ),
    Input: props => (
      <input
        id={props.id}
        readOnly={props.readOnly}
        type={props.type}
        onChange={props.onChange}
        onBlur={props.onBlur}
        value={props.value}
        name={props.name}
        className={props.className}
      />
    ),
    Select,
    TargetValueBadge: () => <div>TargetValueBadge</div>,
    ErrorMessage: props => <div {...props}>{props.children}</div>,
    LoadingIndicator: props => (
      <div className="sk-circle" {...props}>
        Loading...
      </div>
    )
  };
});

jest.mock('services', () => {
  return {
    formatters: {
      duration: a => a
    },
    numberParser: {
      isValidNumber: value => /^[+-]?\d+(\.\d+)?$/.test(value),
      isPositiveNumber: value => /^[+-]?\d+(\.\d+)?$/.test(value) && +value > 0,
      isIntegerNumber: value => /^[+-]?\d+?$/.test(value),
      isFloatNumber: value => /^[+-]?\d+(\.\d+)?$/.test(value)
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
        type: 'flowNodes'
      },
      visualization: 'heat'
    },
    result: {}
  },
  configuration: {
    heatmapTargetValue: {
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
  const node = mount(<DurationHeatmapModal {...validProps} />);

  expect(node).toIncludeText('BPMNDiagram');
});

it('should display a list of flow nodes in a table', async () => {
  const node = mount(<DurationHeatmapModal {...validProps} />);

  await node.setProps({open: true});

  expect(node).toIncludeText('Element A');
  expect(node).toIncludeText('Element B');
  expect(node).toIncludeText('Element C');
});

it('should save the changes target values', async () => {
  const spy = jest.fn();

  const node = mount(<DurationHeatmapModal {...validProps} onConfirm={spy} />);

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
  const node = mount(<DurationHeatmapModal {...validProps} />);

  await node.setProps({open: true});

  expect(node.state('values').a.value).toBe('12');
  expect(node.state('values').a.unit).toBe('days');
});

it('should set isInvalid property for input if value is invalid', async () => {
  const node = mount(<DurationHeatmapModal {...validProps} />);
  await node.setProps({open: true});

  node.instance().setTarget('value', 'a')({target: {value: 'invalid'}});

  await node.update();

  expect(
    node
      .find('.DurationHeatmapModal__selection--input')
      .first()
      .props()
  ).toHaveProperty('isInvalid', true);
});
