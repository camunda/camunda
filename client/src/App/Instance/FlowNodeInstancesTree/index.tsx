/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Bar} from './Bar';
import {Foldable} from './Foldable';
import {Li, NodeDetails, NodeStateIcon, Ul} from './styled';
import {observer} from 'mobx-react';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {getNodeWithMetaData} from './service';
import {TYPE} from 'modules/constants';
import {InstanceState} from 'modules/types';

type Node = {
  id: string;
  type: string;
  state?: InstanceState;
  activityId: string;
  startDate: string;
  endDate: null | string;
  parentId: string;
  children: Node[];
  isLastChild: boolean;
};

type Props = {
  node: Node;
  isSelected: boolean;
  treeDepth: number;
  metaData?: any;
};

function Node({isSelected, node, treeDepth}: Props) {
  const hasChildren = node.children.length > 0;

  return (
    <Li treeDepth={treeDepth} data-testid={`tree-node-${node.id}`}>
      <NodeDetails
        showConnectionDot={treeDepth >= 3}
        data-testid={`treeDepth:${treeDepth}`}
      >
        <NodeStateIcon state={node.state} $indentationMultiplier={treeDepth} />
      </NodeDetails>
      {/* @ts-expect-error ts-migrate(2786) FIXME: Type 'undefined' is not assignable to type 'Elemen... Remove this comment to see the full error message */}
      <Foldable
        isFolded={treeDepth >= 2}
        isFoldable={hasChildren && treeDepth >= 2}
      >
        <Foldable.Summary
          data-testid={node.id}
          onSelection={() => flowNodeInstanceStore.changeCurrentSelection(node)}
          isSelected={isSelected}
          isLastChild={node.isLastChild}
          // @ts-expect-error ts-migrate(2339) FIXME: Property 'name' does not exist on type 'nodePropTy... Remove this comment to see the full error message
          nodeName={`${node.name}${
            node.type === TYPE.MULTI_INSTANCE_BODY ? ` (Multi Instance)` : ''
          }`}
        >
          {/* @ts-expect-error ts-migrate(2741) FIXME: Property 'typeDetails' is missing in type 'nodePro... Remove this comment to see the full error message */}
          <Bar node={node} isSelected={isSelected} />
        </Foldable.Summary>
        {hasChildren && (
          <Foldable.Details>
            <Ul
              showConnectionLine={treeDepth >= 2}
              data-testid={`treeDepth:${treeDepth}`}
            >
              {node.children.map((childNode, index) => {
                return (
                  <FlowNodeInstancesTree
                    key={index}
                    node={{
                      ...childNode,
                      isLastChild: node.children.length === index + 1,
                    }}
                    treeDepth={treeDepth + 1}
                  />
                );
              })}
            </Ul>
          </Foldable.Details>
        )}
      </Foldable>
    </Li>
  );
}

type FlowNodeInstancesTreeProps = {
  node: null | Node;
  treeDepth: number;
};

const FlowNodeInstancesTree: React.FC<FlowNodeInstancesTreeProps> = observer(
  ({treeDepth, node}) => {
    const {
      selection: {treeRowIds},
    } = flowNodeInstanceStore.state;

    const isSelected = node !== null && treeRowIds.includes(node.id);
    const metaData = singleInstanceDiagramStore.getMetaData(node?.activityId);

    return treeDepth === 1 ? (
      <ul>
        <Node
          isSelected={isSelected}
          node={getNodeWithMetaData(
            node,
            metaData,
            currentInstanceStore.state.instance
          )}
          treeDepth={treeDepth}
        />
      </ul>
    ) : (
      <Node
        isSelected={isSelected}
        node={getNodeWithMetaData(
          node,
          metaData,
          currentInstanceStore.state.instance
        )}
        treeDepth={treeDepth}
      />
    );
  }
);

export {FlowNodeInstancesTree};
