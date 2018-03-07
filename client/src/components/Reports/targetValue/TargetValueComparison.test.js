import React from 'react';
import {mount} from 'enzyme';

import TargetValueComparison from './TargetValueComparison';

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
    ButtonGroup: props => <div {...props}>{props.children}</div>,
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

const validPropsWithoutTargetValues = {
  reportResult: validProps.reportResult,
  configuration: {
    targetValue: {
      active: false,
      values: {}
    }
  }
};

const invalidProps = {
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
        type: 'None'
      },
      visualization: 'heat'
    },
    result: {}
  },
  configuration: {
    active: false,
    values: {
      a: {
        value: 12,
        unit: 'days'
      }
    }
  }
};

it('should display a disabled double button', () => {
  const node = mount(<TargetValueComparison {...invalidProps} />);

  expect(node.find('button.TargetValueComparison__toggleButton')).toBePresent();
  expect(node.find('button.TargetValueComparison__toggleButton')).toBeDisabled();
  expect(node.find('button.TargetValueComparison__editButton')).toBePresent();
  expect(node.find('button.TargetValueComparison__editButton')).toBeDisabled();
});

it('should enable the double button if the configuration is valid', () => {
  const node = mount(<TargetValueComparison {...validProps} />);

  expect(node.find('button.TargetValueComparison__toggleButton')).toBePresent();
  expect(node.find('button.TargetValueComparison__toggleButton')).not.toBeDisabled();
  expect(node.find('button.TargetValueComparison__editButton')).toBePresent();
  expect(node.find('button.TargetValueComparison__editButton')).not.toBeDisabled();
});

it('should toggle the mode with the left button', () => {
  const spy = jest.fn();
  const node = mount(<TargetValueComparison {...validProps} onChange={spy} />);

  node.find('button.TargetValueComparison__toggleButton').simulate('click');

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].configuration.targetValue.active).toBe(true);
});

it('should open the modal with the left button if there are no target values set', async () => {
  const node = mount(<TargetValueComparison {...validPropsWithoutTargetValues} />);

  await node.find('button.TargetValueComparison__toggleButton').simulate('click');

  expect(node.state('modalOpen')).toBe(true);
});

it('should open the target value edit modal on with the right button', async () => {
  const node = mount(<TargetValueComparison {...validProps} />);

  await node.find('button.TargetValueComparison__editButton').simulate('click');

  expect(node.state('modalOpen')).toBe(true);
});

it('it should toggle target value view mode off if no target values are defined', async () => {
  const spy = jest.fn();
  const node = mount(<TargetValueComparison {...validProps} onChange={spy} />);

  node.instance().confirmModal({});

  expect(spy).toHaveBeenCalledWith({
    configuration: {
      targetValue: {
        active: false,
        values: {}
      }
    }
  });
});
