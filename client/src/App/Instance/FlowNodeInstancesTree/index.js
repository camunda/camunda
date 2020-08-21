/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {STATE} from 'modules/constants';

import Bar from './Bar';
import Foldable from './Foldable';
import {Li, NodeDetails, NodeStateIcon, Ul} from './styled';
import {observer} from 'mobx-react';
import {flowNodeInstance} from 'modules/stores/flowNodeInstance';
import {currentInstance} from 'modules/stores/currentInstance';
import {singleInstanceDiagram} from 'modules/stores/singleInstanceDiagram';
import {getNodeWithMetaData} from './service';
import {TYPE} from 'modules/constants';

const nodePropType = PropTypes.shape({
  id: PropTypes.string,
  type: PropTypes.string.isRequired,
  activityId: PropTypes.string,
  state: PropTypes.oneOf(Object.values(STATE)),
  children: PropTypes.arrayOf(PropTypes.object),
  isLastChild: PropTypes.bool,
});

function Node({isSelected, node, treeDepth, onTreeRowSelection}) {
  const hasChildren = node.children.length > 0;

  return (
    <Li treeDepth={treeDepth} data-test={`tree-node-${node.id}`}>
      <NodeDetails
        showConnectionDot={treeDepth >= 3}
        data-test={`treeDepth:${treeDepth}`}
      >
        <NodeStateIcon state={node.state} indentationMultiplier={treeDepth} />
      </NodeDetails>
      <Foldable
        isFolded={treeDepth >= 2}
        isFoldable={hasChildren && treeDepth >= 2}
      >
        <Foldable.Summary
          data-test={node.id}
          onSelection={() => onTreeRowSelection(node)}
          isSelected={isSelected}
          isLastChild={node.isLastChild}
          nodeName={`${node.name}${
            node.type === TYPE.MULTI_INSTANCE_BODY ? ` (Multi Instance)` : ''
          }`}
        >
          <Bar node={node} isSelected={isSelected} />
        </Foldable.Summary>
        {hasChildren && (
          <Foldable.Details>
            <Ul
              showConnectionLine={treeDepth >= 2}
              data-test={`treeDepth:${treeDepth}`}
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
                    onTreeRowSelection={onTreeRowSelection}
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

Node.propTypes = {
  node: nodePropType.isRequired,
  isSelected: PropTypes.bool.isRequired,
  treeDepth: PropTypes.number.isRequired,
  metaData: PropTypes.object,
  onTreeRowSelection: PropTypes.func.isRequired,
};

const FlowNodeInstancesTree = observer(
  ({treeDepth, node, onTreeRowSelection}) => {
    const {
      selection: {treeRowIds},
    } = flowNodeInstance.state;
    const isSelected = treeRowIds.includes(node.id);
    const metaData = singleInstanceDiagram.getMetaData(node.activityId);

    return treeDepth === 1 ? (
      <ul>
        <Node
          isSelected={isSelected}
          node={getNodeWithMetaData(
            node,
            metaData,
            currentInstance.state.instance
          )}
          treeDepth={treeDepth}
          onTreeRowSelection={onTreeRowSelection}
        />
      </ul>
    ) : (
      <Node
        isSelected={isSelected}
        node={getNodeWithMetaData(
          node,
          metaData,
          currentInstance.state.instance
        )}
        treeDepth={treeDepth}
        onTreeRowSelection={onTreeRowSelection}
      />
    );
  }
);

FlowNodeInstancesTree.propTypes = {
  node: nodePropType,
  treeDepth: PropTypes.number.isRequired,
  onTreeRowSelection: PropTypes.func.isRequired,
};

export {FlowNodeInstancesTree};
