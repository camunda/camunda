import React from 'react';
import {mount} from 'enzyme';

import {ReactComponent as FlowNodeStartEventIcon} from 'modules/components/Icon/flow-node-start-event.svg';

import {createFlowNodeInstance} from 'modules/testUtils';

import {ThemeProvider} from 'modules/contexts/ThemeContext';

import {NoWrapBar} from './Bar';
import TimeStampLabel from '../TimeStampLabel';

jest.mock(
  '../TimeStampLabel',
  () =>
    function renderMockComponent(props) {
      return <div />;
    }
);

const mockNode = createFlowNodeInstance({
  type: 'START_EVENT',
  id: 'someflowNodeIde',
  name: 'Some Name'
});

const mockOnTreeRowSelection = jest.fn();

const renderComponent = () => {
  const mountedComponent = mount(
    <ThemeProvider>
      <NoWrapBar
        node={mockNode}
        hasBoldTitle={true}
        isSelected={false}
        onTreeRowSelection={mockOnTreeRowSelection}
      />
    </ThemeProvider>
  );

  return mountedComponent.find(NoWrapBar);
};

describe('Bar', () => {
  let node;

  beforeEach(() => {
    node = renderComponent();
  });

  it('should render Node Type Icon', () => {
    expect(node.find(FlowNodeStartEventIcon)).toExist();
  });

  it('should render NodeName', () => {
    expect(node.text()).toContain(mockNode.name);
  });

  it('should render Time Stamp Component', () => {
    expect(node.find(TimeStampLabel)).toExist();
  });

  it('should onClick select a flow node in the diagram', () => {
    node.simulate('click');
    expect(mockOnTreeRowSelection).toHaveBeenCalledWith(mockNode);
  });
});
