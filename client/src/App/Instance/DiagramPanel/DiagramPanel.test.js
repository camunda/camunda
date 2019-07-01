/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import SplitPane from 'modules/components/SplitPane';
import Pane from 'modules/components/SplitPane/Pane';
import StateIcon from 'modules/components/StateIcon';
import {formatDate} from 'modules/utils/date';
import {getWorkflowName} from 'modules/utils/instance';
import {ThemeProvider} from 'modules/theme';
import {createInstance} from 'modules/testUtils';
import * as Styled from './styled';
import DiagramPanel from './DiagramPanel';
import Actions from 'modules/components/Actions';

const mockInstance = createInstance();
const mockProps = {
  forceInstanceSpinner: false,
  onInstanceOperation: jest.fn()
};

const Foo = function({expandState, ...props}) {
  return <div data-test="foo" {...props} />;
};

function BottomPane() {
  return <div data-test="bottom-pane" />;
}
describe('DiagramPanel', () => {
  let node;
  beforeEach(() => {
    node = mount(
      <ThemeProvider>
        <SplitPane>
          <DiagramPanel instance={mockInstance} {...mockProps}>
            <Foo />
          </DiagramPanel>
          <BottomPane />
        </SplitPane>
      </ThemeProvider>
    );
  });

  it('should render pane header and body', () => {
    // given
    const workflowName = getWorkflowName(mockInstance);
    const instanceState = mockInstance.state;
    const formattedStartDate = formatDate(mockInstance.startDate);
    const formattedEndDate = formatDate(mockInstance.endDate);

    // then
    expect(node.find(Pane)).toHaveLength(1);

    // Pane.Header
    const PaneHeaderNode = node.find(Pane.Header);

    const TableNode = PaneHeaderNode.find('table');
    expect(TableNode.text()).toContain(workflowName);
    expect(TableNode.text()).toContain(mockInstance.id);
    expect(TableNode.text()).toContain(
      `Version ${mockInstance.workflowVersion}`
    );
    expect(TableNode.text()).toContain(formattedStartDate);
    expect(TableNode.text()).toContain(formattedEndDate);

    const StateIconNode = TableNode.find(StateIcon);
    expect(StateIconNode).toHaveLength(1);
    expect(StateIconNode.prop('state')).toBe(instanceState);

    const ActionsNode = PaneHeaderNode.find(Actions);
    expect(ActionsNode).toExist();
    expect(ActionsNode.props().instance).toEqual(mockInstance);
    expect(ActionsNode.props().forceSpinner).toEqual(
      mockProps.forceInstanceSpinner
    );
    expect(ActionsNode.props().onButtonClick).toEqual(
      mockProps.onInstanceOperation
    );

    // Pane.Body
    const PaneBodyNode = node.find(Styled.SplitPaneBody);
    expect(PaneBodyNode).toExist();

    // Child
    expect(PaneBodyNode.find(Foo)).toHaveLength(1);
  });

  it('should pass expandState to children', () => {
    expect(node.find(Foo).props()).toEqual({expandState: 'DEFAULT'});
  });
});
