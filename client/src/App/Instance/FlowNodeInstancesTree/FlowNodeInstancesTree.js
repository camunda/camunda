import React, {Fragment} from 'react';
import PropTypes from 'prop-types';

import Foldable from './Foldable';
import {STATE} from 'modules/constants';

import Bar from './Bar';
import * as Styled from './styled';

export default class FlowNodeInstancesTree extends React.Component {
  static propTypes = {
    node: PropTypes.shape({
      id: PropTypes.string,
      type: PropTypes.string.isRequired,
      activityId: PropTypes.string,
      state: PropTypes.oneOf(Object.values(STATE)),
      children: PropTypes.array
    }),
    selectedTreeRowIds: PropTypes.array.isRequired,
    treeDepth: PropTypes.number.isRequired,
    onTreeRowSelection: PropTypes.func.isRequired,
    getNodeWithName: PropTypes.func.isRequired
  };

  renderNode = () => {
    const {node, treeDepth} = this.props;
    const hasChildren = node.children.length > 0;

    return (
      <Styled.Li data-test={node.id}>
        <Styled.NodeDetails
          showConnectionDot={treeDepth >= 3}
          data-test={`treeDepth:${treeDepth}`}
        >
          <Styled.NodeStateIcon
            state={node.state}
            positionMultiplier={treeDepth}
          />
        </Styled.NodeDetails>
        <Foldable isFoldable={treeDepth >= 2 && hasChildren}>
          <Foldable.Summary data-test={node.id}>
            <Bar
              node={this.props.getNodeWithName(node)}
              hasBoldTitle={!!node.children}
              isSelected={this.props.selectedTreeRowIds.includes(node.id)}
              onTreeRowSelection={this.props.onTreeRowSelection}
            />
          </Foldable.Summary>
          {hasChildren && this.renderChildren()}
        </Foldable>
      </Styled.Li>
    );
  };

  renderChildren = () => {
    const {node, treeDepth} = this.props;

    return (
      <Foldable.Details>
        <Styled.Ul
          showConnectionLine={treeDepth >= 2}
          data-test={`treeDepth:${treeDepth}`}
        >
          {node.children.map((childNode, index) => {
            return (
              <FlowNodeInstancesTree
                key={index}
                node={childNode}
                treeDepth={treeDepth + 1}
                selectedTreeRowIds={this.props.selectedTreeRowIds}
                onTreeRowSelection={this.props.onTreeRowSelection}
                getNodeWithName={this.props.getNodeWithName}
              />
            );
          })}
        </Styled.Ul>
      </Foldable.Details>
    );
  };

  render() {
    if (this.props.treeDepth === 1) {
      return <ul> {this.renderNode()}</ul>;
    }

    return <Fragment>{this.renderNode()}</Fragment>;
  }
}
