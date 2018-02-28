import React from 'react';
import {mount} from 'enzyme';

import ControlPanel from './ControlPanel';

jest.mock('../Reports', () => {return {
  Filter: () => 'Filter'
}});

jest.mock('components', () => {
  return {
    ActionItem: props => <button {...props}>{props.children}</button>,
    Popover: ({children}) => children,
    ProcessDefinitionSelection: (props) => <div>ProcessDefinitionSelection</div>
  };
});

const data = {
  processDefinitionKey: '',
  processDefinitionVersion: '',
  filter: null
};

const spy = jest.fn();

it('should contain a gateway and end Event field', () => {
  const node = mount(<ControlPanel {...data} onChange={spy} />);

  expect(node.find('[name="ControlPanel__gateway"]')).toBePresent();
  expect(node.find('[name="ControlPanel__endEvent"]')).toBePresent();
});

it('should show a please select message if an entity is not selected', () => {
  const node = mount(<ControlPanel {...data} onChange={spy} />);

  expect(node).toIncludeText('Please Select End Event');
  expect(node).toIncludeText('Please Select Gateway');
});

it('should show the element name if an element is selected', () => {
  const node = mount(<ControlPanel {...data} onChange={spy} gateway={{
    name: 'I am a Gateway',
    id: 'gatewayId'
  }} />);

  expect(node).toIncludeText('I am a Gateway');
  expect(node).not.toIncludeText('gatewayId');
});

it('should show the element id if an element has no name', () => {
  const node = mount(<ControlPanel {...data} onChange={spy} gateway={{
    name: undefined,
    id: 'gatewayId'
  }} />);

  expect(node).toIncludeText('gatewayId');

})
