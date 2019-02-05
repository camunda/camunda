import React from 'react';
import {mount} from 'enzyme';

import SplitPane from 'modules/components/SplitPane';
import Pane from 'modules/components/SplitPane/Pane';
import StateIcon from 'modules/components/StateIcon';
import {formatDate} from 'modules/utils/date';
import {getWorkflowName, getInstanceState} from 'modules/utils/instance';
import {ThemeProvider} from 'modules/theme';
import {createInstance} from 'modules/testUtils';

import DiagramPanel from './DiagramPanel';
import DiagramBar from './DiagramBar';

const mockInstance = createInstance();

function Foo(props) {
  return <div data-test="foo" {...props} />;
}

function BottomPane() {
  return <div data-test="bottom-pane" />;
}
describe('DiagramPanel', () => {
  it('should render pane header and body', () => {
    // given
    const workflowName = getWorkflowName(mockInstance);
    const instanceState = getInstanceState(mockInstance);
    const formattedStartDate = formatDate(mockInstance.startDate);
    const formattedEndDate = formatDate(mockInstance.endDate);
    const node = mount(
      <ThemeProvider>
        <SplitPane>
          <DiagramPanel instance={mockInstance}>
            <Foo />
          </DiagramPanel>
          <BottomPane />
        </SplitPane>
      </ThemeProvider>
    );

    // then
    expect(node.find(Pane)).toHaveLength(1);

    // Pane.Header
    const PaneHeaderNode = node.find(Pane.Header);
    const TableNode = PaneHeaderNode.find('table');
    const StateIconNode = TableNode.find(StateIcon);
    expect(StateIconNode).toHaveLength(1);
    expect(StateIconNode.prop('state')).toBe(instanceState);
    expect(TableNode.text()).toContain(workflowName);
    expect(node.find(Pane.Body)).toHaveLength(1);
    expect(TableNode.text()).toContain(mockInstance.id);
    expect(TableNode.text()).toContain(
      `Version ${mockInstance.workflowVersion}`
    );
    expect(TableNode.text()).toContain(formattedStartDate);
    expect(TableNode.text()).toContain(formattedEndDate);

    // Pane.Body
    const PaneBodyNode = node.find(Pane.Body);
    expect(PaneBodyNode).toHaveLength(1);
    // DiagramBar
    const DiagramBarNode = PaneBodyNode.find(DiagramBar);
    expect(DiagramBarNode).toHaveLength(1);
    expect(DiagramBarNode.prop('instance')).toBe(mockInstance);
    // Child
    expect(PaneBodyNode.find(Foo)).toHaveLength(1);
  });
});
