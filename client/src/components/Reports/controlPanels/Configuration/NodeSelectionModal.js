/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';

import {Modal, Button, BPMNDiagram, ClickBehavior} from 'components';

import './NodeSelectionModal.scss';

export default class NodeSelectionModal extends Component {
  constructor(props) {
    super(props);

    const hiddenNodes = props.report.data.configuration.hiddenNodes || [];

    const visibleFlowNodes = getFlowNodesKeys(props.report).filter(
      key => !hiddenNodes.includes(key)
    );

    this.state = {
      selectedNodes: visibleFlowNodes
    };
  }

  toggleNode = toggledNode => {
    if (this.state.selectedNodes.includes(toggledNode.id)) {
      this.setState({
        selectedNodes: this.state.selectedNodes.filter(node => node !== toggledNode.id)
      });
    } else {
      this.setState({
        selectedNodes: this.state.selectedNodes.concat([toggledNode.id])
      });
    }
  };

  applyFilter = () => {
    const selected = this.state.selectedNodes;
    const hiddenNodes = getFlowNodesKeys(this.props.report).filter(key => !selected.includes(key));

    this.props.onChange({hiddenNodes: {$set: hiddenNodes}});
    this.props.onClose();
  };

  isNodeSelected = () => {
    return this.state.selectedNodes.length > 0;
  };

  isAllSelected = () => {
    return getFlowNodesKeys(this.props.report).length === this.state.selectedNodes.length;
  };

  selectAll = async () => {
    this.setState({
      selectedNodes: getFlowNodesKeys(this.props.report)
    });
  };

  deselectAll = () => {
    this.setState({selectedNodes: []});
  };

  render() {
    return (
      <Modal
        open={true}
        onClose={this.props.onClose}
        onConfirm={this.isNodeSelected() ? this.applyFilter : undefined}
        className="NodeSelectionModal"
        size="max"
      >
        <Modal.Header>Add Flow Node Filter</Modal.Header>
        <Modal.Content className="modalContent">
          <div className="diagramActions">
            <p>Selected nodes appear in the visualization.</p>
            <Button disabled={this.isAllSelected()} onClick={this.selectAll}>
              Select All
            </Button>
            <Button disabled={!this.isNodeSelected()} onClick={this.deselectAll}>
              Deselect All
            </Button>
          </div>
          <div className="diagramContainer">
            <BPMNDiagram xml={this.props.report.data.configuration.xml}>
              <ClickBehavior onClick={this.toggleNode} selectedNodes={this.state.selectedNodes} />
            </BPMNDiagram>
          </div>
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.onClose}>Cancel</Button>
          <Button
            type="primary"
            color="blue"
            disabled={!this.isNodeSelected()}
            onClick={this.applyFilter}
          >
            Apply
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}

function getFlowNodesKeys(report) {
  return report.result.data.map(({key}) => key);
}
