/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';

import {Modal, Button, BPMNDiagram, ClickBehavior} from 'components';

import './NodeSelectionModal.scss';
import {t} from 'translation';

export default class NodeSelectionModal extends Component {
  constructor(props) {
    super(props);

    const hiddenNodes = props.report.data.configuration.hiddenNodes.keys || [];

    const visibleFlowNodes = getFlowNodesKeys(props.report).filter(
      key => !hiddenNodes.includes(key)
    );

    this.state = {
      selectedNodes: visibleFlowNodes
    };
  }

  toggleNode = toggledNode => {
    this.setState(({selectedNodes}) => {
      if (selectedNodes.includes(toggledNode.id)) {
        return {selectedNodes: selectedNodes.filter(node => node !== toggledNode.id)};
      } else {
        return {selectedNodes: selectedNodes.concat([toggledNode.id])};
      }
    });
  };

  applyConfiguration = () => {
    const selected = this.state.selectedNodes;
    const hiddenNodes = getFlowNodesKeys(this.props.report).filter(key => !selected.includes(key));

    this.props.onChange({hiddenNodes: {keys: {$set: hiddenNodes}}});
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
    const nodeType = this.props.report.data.view.entity === 'userTask' ? 'UserTask' : 'FlowNode';

    return (
      <Modal
        open
        onClose={this.props.onClose}
        onConfirm={this.isNodeSelected() ? this.applyConfiguration : undefined}
        className="NodeSelectionModal"
        size="max"
      >
        <Modal.Header>{t('report.config.visibleNodes.modal.title')}</Modal.Header>
        <Modal.Content className="modalContent">
          <div className="diagramActions">
            <p>{t('report.config.visibleNodes.modal.description')}</p>
            <Button disabled={this.isAllSelected()} onClick={this.selectAll}>
              {t('common.selectAll')}
            </Button>
            <Button disabled={!this.isNodeSelected()} onClick={this.deselectAll}>
              {t('common.deselectAll')}
            </Button>
          </div>
          <div className="diagramContainer">
            <BPMNDiagram xml={this.props.report.data.configuration.xml}>
              <ClickBehavior
                onClick={this.toggleNode}
                selectedNodes={this.state.selectedNodes}
                nodeType={nodeType}
              />
            </BPMNDiagram>
          </div>
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.onClose}>{t('common.cancel')}</Button>
          <Button
            variant="primary"
            color="blue"
            disabled={!this.isNodeSelected()}
            onClick={this.applyConfiguration}
          >
            {t('common.apply')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}

function getFlowNodesKeys(report) {
  return report.result.data.map(({key}) => key);
}
