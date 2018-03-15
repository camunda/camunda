import React from 'react';
import {mount} from 'enzyme';

import {extractProcessDefinitionName} from 'services';

import ControlPanel from './ControlPanel';

jest.mock('../Reports', () => {
  return {
    Filter: () => 'Filter'
  };
});

jest.mock('components', () => {
  return {
    ActionItem: props => (
      <button id="actionItem" {...props}>
        {props.children}
      </button>
    ),
    Popover: ({title, children}) => (
      <div>
        {title} {children}
      </div>
    ),
    ProcessDefinitionSelection: props => <div>ProcessDefinitionSelection</div>
  };
});

jest.mock('services', () => {
  return {
    extractProcessDefinitionName: jest.fn()
  };
});

const data = {
  processDefinitionKey: 'aKey',
  processDefinitionVersion: 'aVersion',
  filter: null,
  xml: 'aFooXml'
};

const emptyData = {
  processDefinitionKey: '',
  processDefinitionVersion: '',
  filter: null,
  xml: null
};

extractProcessDefinitionName.mockReturnValue('foo');
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
  const node = mount(
    <ControlPanel
      {...data}
      onChange={spy}
      gateway={{
        name: 'I am a Gateway',
        id: 'gatewayId'
      }}
    />
  );

  expect(node).toIncludeText('I am a Gateway');
  expect(node).not.toIncludeText('gatewayId');
});

it('should show the element id if an element has no name', () => {
  const node = mount(
    <ControlPanel
      {...data}
      onChange={spy}
      gateway={{
        name: undefined,
        id: 'gatewayId'
      }}
    />
  );

  expect(node).toIncludeText('gatewayId');
});

it('should show initially show process definition name if xml is available', async () => {
  extractProcessDefinitionName.mockReturnValue('aName');

  const node = await mount(<ControlPanel {...data} />);

  expect(node.find('.ControlPanel__popover')).toIncludeText('aName');
});

it('should change process definition name if process definition xml is updated', async () => {
  const node = await mount(<ControlPanel {...data} />);

  extractProcessDefinitionName.mockReturnValue('aName');
  await node.setProps({xml: 'barXml'});

  expect(node.find('.ControlPanel__popover')).toIncludeText('aName');
});

it('should disable gateway and EndEvent elements if no ProcDef selected', async () => {
  const node = await mount(<ControlPanel hoveredControl="gateway" {...emptyData} />);

  expect(node.find('#actionItem').first()).toBeDisabled();
  expect(node.find('#actionItem').at(1)).toBeDisabled();

  expect(node.find('.ControlPanel__config').at(1)).not.toHaveClassName(
    'ControlPanel__config--hover'
  );
});
