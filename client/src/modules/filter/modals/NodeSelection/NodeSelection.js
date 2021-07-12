/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import Viewer from 'bpmn-js/lib/NavigatedViewer';

import {Modal, Button, BPMNDiagram, ClickBehavior} from 'components';
import {t} from 'translation';

import './NodeSelection.scss';

export default class NodeSelection extends React.Component {
  state = {
    allFlowNodes: [],
    selectedNodes: [],
  };

  async componentDidMount() {
    const viewer = new Viewer();
    await viewer.importXML(this.props.xml);

    const flowNodes = new Set();
    viewer
      .get('elementRegistry')
      .filter((element) => element.businessObject.$instanceOf('bpmn:FlowNode'))
      .map((element) => element.businessObject)
      .forEach((element) => flowNodes.add(element.id));
    const allFlowNodes = Array.from(flowNodes);

    let preExistingValues;
    if (this.props.filterData?.data.values) {
      preExistingValues = allFlowNodes.filter(
        (id) => !this.props.filterData?.data.values.includes(id)
      );
    }

    this.setState({
      allFlowNodes,
      selectedNodes: preExistingValues || allFlowNodes,
    });
  }

  toggleNode = (toggledNode) => {
    this.setState(({selectedNodes}) => {
      if (selectedNodes.includes(toggledNode.id)) {
        return {selectedNodes: selectedNodes.filter((node) => node !== toggledNode.id)};
      } else {
        return {selectedNodes: selectedNodes.concat([toggledNode.id])};
      }
    });
  };

  createFilter = () => {
    const {allFlowNodes, selectedNodes} = this.state;

    this.props.addFilter({
      type: 'executedFlowNodes',
      data: {operator: 'not in', values: allFlowNodes.filter((id) => !selectedNodes.includes(id))},
      appliedTo: [this.props.definitions[0].identifier],
    });
  };

  isNodeSelected = () => {
    return this.state.selectedNodes.length > 0;
  };

  isAllSelected = () => {
    return this.state.allFlowNodes.length === this.state.selectedNodes.length;
  };

  selectAll = () => {
    this.setState({
      selectedNodes: this.state.allFlowNodes,
    });
  };

  deselectAll = () => {
    this.setState({selectedNodes: []});
  };

  isValidSelection = () => {
    return this.isNodeSelected() && !this.isAllSelected();
  };

  render() {
    return (
      <Modal
        open
        onClose={this.props.close}
        onConfirm={this.isValidSelection() ? this.createFilter : undefined}
        className="NodeSelection"
        size="max"
      >
        <Modal.Header>{t('common.filter.types.flowNodeSelection')}</Modal.Header>
        <Modal.Content className="modalContent">
          <div className="diagramActions">
            <Button disabled={false} onClick={this.selectAll}>
              {t('common.selectAll')}
            </Button>
            <Button disabled={!this.isNodeSelected()} onClick={this.deselectAll}>
              {t('common.deselectAll')}
            </Button>
          </div>
          <div className="diagramContainer">
            <BPMNDiagram xml={this.props.xml}>
              <ClickBehavior
                onClick={this.toggleNode}
                selectedNodes={this.state.selectedNodes}
                nodeTypes={['FlowNode']}
              />
            </BPMNDiagram>
          </div>
        </Modal.Content>
        <Modal.Actions>
          <Button main onClick={this.props.close}>
            {t('common.cancel')}
          </Button>
          <Button main primary disabled={!this.isValidSelection()} onClick={this.createFilter}>
            {this.props.filterData ? t('common.filter.updateFilter') : t('common.filter.addFilter')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}
