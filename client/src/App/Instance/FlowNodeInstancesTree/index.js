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
import * as Styled from './styled';
import {observer} from 'mobx-react';
import {flowNodeInstance} from 'modules/stores/flowNodeInstance';
import {currentInstance} from 'modules/stores/currentInstance';
import {singleInstanceDiagram} from 'modules/stores/singleInstanceDiagram';
import {getNodeWithMetaData} from './service';

const FlowNodeInstancesTree = observer(
  class FlowNodeInstancesTree extends React.Component {
    static propTypes = {
      node: PropTypes.shape({
        id: PropTypes.string,
        type: PropTypes.string.isRequired,
        activityId: PropTypes.string,
        state: PropTypes.oneOf(Object.values(STATE)),
        children: PropTypes.arrayOf(PropTypes.object),
        isLastChild: PropTypes.bool,
      }),
      treeDepth: PropTypes.number.isRequired,
      onTreeRowSelection: PropTypes.func.isRequired,
    };

    renderNode = () => {
      const {node, treeDepth} = this.props;
      const {
        selection: {treeRowIds: selectedTreeRowIds},
      } = flowNodeInstance.state;
      const hasChildren = node.children.length > 0;
      const isSelected = selectedTreeRowIds.includes(node.id);
      const metaData = singleInstanceDiagram.getMetaData(node.activityId);

      return (
        <Styled.Li treeDepth={treeDepth} data-test={`tree-node-${node.id}`}>
          <Styled.NodeDetails
            showConnectionDot={treeDepth >= 3}
            data-test={`treeDepth:${treeDepth}`}
          >
            <Styled.NodeStateIcon
              state={node.state}
              indentationMultiplier={treeDepth}
            />
          </Styled.NodeDetails>
          <Foldable
            isFolded={treeDepth >= 2}
            isFoldable={hasChildren && treeDepth >= 2}
          >
            <Foldable.Summary
              data-test={node.id}
              onSelection={() => this.props.onTreeRowSelection(node)}
              isSelected={isSelected}
              isLastChild={node.isLastChild}
            >
              <Bar
                node={getNodeWithMetaData(
                  node,
                  metaData,
                  currentInstance.state.instance
                )}
                isSelected={isSelected}
              />
            </Foldable.Summary>
            {hasChildren && (
              <Foldable.Details>{this.renderChildren()}</Foldable.Details>
            )}
          </Foldable>
        </Styled.Li>
      );
    };

    renderChildren = () => (
      <Styled.Ul
        showConnectionLine={this.props.treeDepth >= 2}
        data-test={`treeDepth:${this.props.treeDepth}`}
      >
        {this.props.node.children.map((childNode, index) => {
          return (
            <FlowNodeInstancesTree
              key={index}
              node={{
                ...childNode,
                isLastChild: this.props.node.children.length === index + 1,
              }}
              treeDepth={this.props.treeDepth + 1}
              onTreeRowSelection={this.props.onTreeRowSelection}
            />
          );
        })}
      </Styled.Ul>
    );

    render() {
      const wrapRootNode = (condition, child) =>
        condition ? <ul>{child}</ul> : <>{child}</>;

      return wrapRootNode(this.props.treeDepth === 1, this.renderNode());
    }
  }
);

export {FlowNodeInstancesTree};
