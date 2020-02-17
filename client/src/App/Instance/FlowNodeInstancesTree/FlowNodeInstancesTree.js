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

export default class FlowNodeInstancesTree extends React.Component {
  static propTypes = {
    node: PropTypes.shape({
      id: PropTypes.string,
      type: PropTypes.string.isRequired,
      activityId: PropTypes.string,
      state: PropTypes.oneOf(Object.values(STATE)),
      children: PropTypes.arrayOf(PropTypes.object),
      isLastChild: PropTypes.bool
    }),
    selectedTreeRowIds: PropTypes.array.isRequired,
    treeDepth: PropTypes.number.isRequired,
    onTreeRowSelection: PropTypes.func.isRequired,
    getNodeWithMetaData: PropTypes.func.isRequired
  };

  renderNode = () => {
    const {node, treeDepth, selectedTreeRowIds} = this.props;

    const hasChildren = node.children.length > 0;
    const isSelected = selectedTreeRowIds.includes(node.id);

    return (
      <Styled.Li treeDepth={treeDepth} data-test={node.id}>
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
              node={this.props.getNodeWithMetaData(node)}
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
              isLastChild: this.props.node.children.length === index + 1
            }}
            treeDepth={this.props.treeDepth + 1}
            selectedTreeRowIds={this.props.selectedTreeRowIds}
            onTreeRowSelection={this.props.onTreeRowSelection}
            getNodeWithMetaData={this.props.getNodeWithMetaData}
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
