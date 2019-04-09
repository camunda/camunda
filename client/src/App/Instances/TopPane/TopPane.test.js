/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';

import Diagram from 'modules/components/Diagram';
import Panel from 'modules/components/Panel';

import SplitPane from 'modules/components/SplitPane';
import EmptyMessage from '../EmptyMessage';
import TopPane from './';

jest.mock(
  'modules/components/Diagram',
  () =>
    function Diagram(props) {
      return <div data-test="diagram-mock" />;
    }
);

let mockProps = {
  workflowName: 'MockWorkflowName',
  renderNoVersionMessage: false,
  renderNoWorkflowMessage: false,
  renderChildren: true
};

const paneProps = {
  paneId: 'TOP',
  handleExpand: jest.fn(),
  expandState: 'DEFAULT',
  titles: {bottom: 'Instances', top: 'Workflow'}
};

const renderNode = (mockProps, paneProps) => {
  const node = mount(
    <ThemeProvider>
      <CollapsablePanelProvider>
        <TopPane {...mockProps} {...paneProps}>
          <Diagram />
        </TopPane>
      </CollapsablePanelProvider>
    </ThemeProvider>
  );
  return node.find(TopPane);
};

describe('TopPane', () => {
  let node;

  it('should render pane header with workflow name', () => {
    //given
    node = renderNode(mockProps, paneProps);

    //when
    expect(
      node
        .find(Panel.Header)
        .find("[data-test='instances-diagram-title']")
        .text()
    ).toBe(mockProps.workflowName);
  });

  it('should pass on all required props for collapsing to the Pane wrapper', () => {
    node = renderNode(mockProps, paneProps);

    expect(node.find(SplitPane.Pane).props()).toMatchObject(paneProps);
  });

  it('should render only its children', () => {
    node = renderNode(mockProps, paneProps);
    expect(node.find(TopPane).find(Diagram)).toExist();
    expect(node.find(TopPane).find(EmptyMessage)).not.toExist();
  });

  it('should render only the "no workflow selected" message', () => {
    //given
    const customMockProps = {
      ...mockProps,
      renderNoVersionMessage: true,
      renderChildren: false
    };

    node = renderNode(customMockProps, paneProps);

    expect(node.find(TopPane).find(Diagram)).not.toExist();
    expect(node.find(TopPane).find(EmptyMessage)).toExist();
    expect(
      node
        .find(TopPane)
        .find(EmptyMessage)
        .text()
    ).toContain(
      `There is more than one version selected for Workflow "${
        mockProps.workflowName
      }". To see a diagram, select a single version.`
    );
  });

  it('should only render only the "no workflow version selected" message', () => {
    const customMockProps = {
      ...mockProps,
      renderNoWorkflowMessage: true,
      renderChildren: false
    };

    node = renderNode(customMockProps, paneProps);

    expect(node.find(TopPane).find(Diagram)).not.toExist();
    expect(node.find(TopPane).find(EmptyMessage)).toExist();
    expect(
      node
        .find(TopPane)
        .find(EmptyMessage)
        .text()
    ).toContain(
      `There is no Workflow selected. To see a diagram, select a Workflow in the Filters panel.`
    );
  });

  it('should only render one component at a time or none', () => {
    const customMockProps = {
      ...mockProps,
      renderNoWorkflowMessage: true,
      renderChildren: true
    };

    node = renderNode(customMockProps, paneProps);

    expect(node.find(TopPane).find(Diagram)).not.toExist();
    expect(node.find(TopPane).find(EmptyMessage)).not.toExist();
  });

  it('should not render any component when the panel is collapsed', () => {
    const customPaneProps = {
      ...paneProps,
      expandState: 'COLLAPSED'
    };

    node = renderNode(mockProps, customPaneProps);

    expect(node.find(TopPane).find(Diagram)).not.toExist();
    expect(node.find(TopPane).find(EmptyMessage)).not.toExist();
  });
});
