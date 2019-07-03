/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {ReactComponent as FoldableRightIcon} from 'modules/components/Icon/right.svg';

import {ThemeProvider} from 'modules/contexts/ThemeContext';

import * as Styled from './styled';
import FlowNodeInstancesTree from './FlowNodeInstancesTree';
import Foldable from './Foldable';
import * as FoldableStyles from './Foldable/styled';

import {testData} from './FlowNodeInstancesTree.setup';

// Mock TimeStampLabel node;

jest.mock(
  './Bar',
  () =>
    function renderMockComponent() {
      return <div data-test="BarComponent" />;
    }
);

const mockOnSelection = jest.fn();

const mountNode = customProps => {
  const mountedNode = mount(
    <ThemeProvider>
      <FlowNodeInstancesTree
        node={testData.parentNode}
        selectedTreeRowIds={[]}
        onTreeRowSelection={mockOnSelection}
        getFlowNodeDetails={jest.fn()}
        getNodeWithMetaData={node => node.id}
        treeDepth={1}
        {...customProps}
      />
    </ThemeProvider>
  );
  return mountedNode.find(FlowNodeInstancesTree);
};

describe('FlowNodeInstancesTree', () => {
  let node;
  let StartEventNode;
  let SubProcessNode;
  let ServiceNode;

  beforeEach(() => {
    node = mountNode();
    // specific nodes
    StartEventNode = node.find(`[data-test="StartEventId"]`).find('li');
    SubProcessNode = node
      .find(Styled.Li)
      .find('li')
      .find(`[data-test="SubProcessId"]`);

    ServiceNode = node.find(`[data-test="ServiceTaskId"]`).find('li');
  });

  it('should show Connection-Line for scoped nodes starting from tree level 2', () => {
    expect(
      node
        .find(Styled.Ul)
        .find('[showConnectionLine=false]')
        .find('[data-test="treeDepth:1"]')
    ).toExist();

    expect(
      node
        .find(Styled.Ul)
        .find('[showConnectionLine=true]')
        .find('[data-test="treeDepth:2"]')
    ).toExist();
  });

  it('should show Connection-Dot for each node starting from tree level 3', () => {
    expect(
      node
        .find(Styled.NodeDetails)
        .find('[data-test="treeDepth:2"]')
        .find('[showConnectionDot=true]')
    ).not.toExist();

    expect(
      node
        .find(Styled.NodeDetails)
        .find('[data-test="treeDepth:3"]')
        .find('[showConnectionDot=true]')
    ).toExist();
  });

  it('should render a Bar component', () => {
    const barComponent = StartEventNode.find('[data-test="BarComponent"]');

    //then
    expect(barComponent).toExist();
  });

  describe('FlowNode Instance Selection', () => {
    it('should show root element as default selection', () => {
      //Given
      node = mountNode({
        selectedTreeRowIds: testData.multipleSelectedTreeRowIds
      });
      //Then
      const rootNode = node.find(`[data-test="ParentNodeId"]`);

      expect(rootNode.find(`[isSelected=true]`)).toExist();
    });

    it('should call the OnSelect method from props with node obj of clicked row', () => {
      //Given
      node = mountNode({
        selectedTreeRowIds: testData.multipleSelectedTreeRowIds
      });
      const StartEventNodeButton = node
        .find(`[data-test="StartEventId"]`)
        .find(Foldable.Summary)
        .find(FoldableStyles.FocusButton);

      // When
      StartEventNodeButton.simulate('click');
      //Then
      expect(mockOnSelection).toHaveBeenCalledWith(testData.startEventInstance);
    });
  });

  describe('Child Scopes', () => {
    it('should render a dropdown element if a child scope exists', () => {
      //given
      expect(SubProcessNode.find(Foldable.Summary)).toExist();
      expect(StartEventNode.find(Foldable.Summary)).toExist();
      //then
      expect(StartEventNode.find(Foldable.Details)).not.toExist();
      expect(SubProcessNode.find(Foldable.Details)).toExist();
    });

    it('should render an folding button, when a child scope exists', () => {
      expect(StartEventNode.find(FoldableRightIcon)).not.toExist();
      expect(SubProcessNode.find(FoldableRightIcon)).toExist();
    });

    it('the parent node should not render a folding button', () => {
      expect(
        node
          .find(Foldable.Summary)
          .find(`[data-test="${testData.parentNode.id}"]`)
          .find(FoldableRightIcon)
      ).not.toExist();
    });
  });

  describe('icons', () => {
    it('should render a State Icon', () => {
      expect(StartEventNode.find(Styled.NodeStateIcon)).toHaveLength(1);
    });

    it('nodes should receive a indentationMultiplier to position the state Icon correctly', () => {
      // On Tree level: 1
      expect(
        node
          .find(`[data-test="${testData.parentNode.id}"]`)
          .find(Styled.NodeStateIcon)
          .find('[indentationMultiplier=1]')
      ).toExist();

      // On Tree level: 2
      expect(
        StartEventNode.find(Styled.NodeStateIcon).find(
          '[indentationMultiplier=2]'
        )
      ).toExist();

      // On Tree level: 3
      expect(
        ServiceNode.find(Styled.NodeStateIcon).find('[indentationMultiplier=3]')
      ).toExist();
    });
  });
});
