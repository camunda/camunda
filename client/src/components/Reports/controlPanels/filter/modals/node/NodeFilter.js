/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Modal, ButtonGroup, Button, BPMNDiagram, ClickBehavior} from 'components';

import './NodeFilter.scss';

export default class NodeFilter extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      selectedNodes: this.props.filterData ? this.props.filterData.data.values : [],
      operator: this.props.filterData ? this.props.filterData.data.operator : 'in'
    };
  }

  toggleNode = toggledNode => {
    if (this.state.selectedNodes.includes(toggledNode)) {
      this.setState({
        selectedNodes: this.state.selectedNodes.filter(node => node !== toggledNode)
      });
    } else {
      this.setState({
        selectedNodes: this.state.selectedNodes.concat([toggledNode])
      });
    }
  };

  createFilter = () => {
    const values = this.state.selectedNodes.map(node => node.id);

    this.props.addFilter({
      type: 'executedFlowNodes',
      data: {
        operator: this.state.operator,
        values
      }
    });
  };

  isNodeSelected = () => {
    return this.state.selectedNodes.length > 0;
  };

  createOperator = name => {
    return <span className="previewItemOperator"> {name} </span>;
  };

  createPreviewList = () => {
    const previewList = [];

    this.state.selectedNodes.forEach((selectedNode, idx) => {
      previewList.push(
        <li key={idx} className="previewItem">
          <span key={idx}>
            {' '}
            <span className="previewItemValue">{selectedNode.name || selectedNode.id}</span>{' '}
            {idx < this.state.selectedNodes.length - 1 &&
              this.createOperator(this.state.operator === 'in' ? 'or' : 'nor')}
          </span>
        </li>
      );
    });
    return (
      <div className="preview">
        <span>This is the filter you are about to create: </span>{' '}
        <span className="parameterName">Executed Flow Node</span>
        {this.createOperator(
          this.state.operator === 'in'
            ? 'is'
            : this.state.selectedNodes.length > 1
            ? 'is neither'
            : 'is not'
        )}
        <ul className="previewList">{previewList}</ul>
      </div>
    );
  };

  setSelectedNodes = nodes => {
    this.setState({
      selectedNodes: nodes
    });
  };

  render() {
    return (
      <Modal
        open={true}
        onClose={this.props.close}
        onConfirm={this.isNodeSelected() ? this.createFilter : undefined}
        className="NodeFilter"
        size="max"
      >
        <Modal.Header>Add Flow Node Filter</Modal.Header>
        <Modal.Content className="modalContent">
          {this.createPreviewList()}
          <ButtonGroup>
            <Button
              active={this.state.operator === 'in'}
              onClick={() => this.setState({operator: 'in'})}
            >
              was executed
            </Button>
            <Button
              active={this.state.operator === 'not in'}
              onClick={() => this.setState({operator: 'not in'})}
            >
              was not executed
            </Button>
          </ButtonGroup>
          {this.props.xml && (
            <div className="diagramContainer">
              <BPMNDiagram xml={this.props.xml}>
                <ClickBehavior
                  setSelectedNodes={this.setSelectedNodes}
                  onClick={this.toggleNode}
                  selectedNodes={this.state.selectedNodes}
                />
              </BPMNDiagram>
            </div>
          )}
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.close}>Cancel</Button>
          <Button
            variant="primary"
            color="blue"
            disabled={!this.isNodeSelected()}
            onClick={this.createFilter}
          >
            {this.props.filterData ? 'Edit ' : 'Add '}Filter
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}
