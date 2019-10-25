/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Modal, ButtonGroup, Button, BPMNDiagram, ClickBehavior} from 'components';

import './NodeFilter.scss';
import {t} from 'translation';
import NodeListPreview from './NodeListPreview';

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
    const {operator} = this.state;
    const type = operator ? 'executedFlowNodes' : 'executingFlowNodes';
    this.props.addFilter({type, data: {operator, values}});
  };

  isNodeSelected = () => {
    return this.state.selectedNodes.length > 0;
  };

  setSelectedNodes = nodes => {
    this.setState({
      selectedNodes: nodes
    });
  };

  render() {
    const {selectedNodes, operator} = this.state;
    return (
      <Modal
        open={true}
        onClose={this.props.close}
        onConfirm={this.isNodeSelected() ? this.createFilter : undefined}
        className="NodeFilter"
        size="max"
      >
        <Modal.Header>
          {t('common.filter.modalHeader', {
            type: t(`common.filter.types.flowNode`)
          })}
        </Modal.Header>
        <Modal.Content className="modalContent">
          <div className="preview">
            <span>{t('common.filter.nodeModal.previewLabel')}</span>{' '}
            <NodeListPreview nodes={selectedNodes} operator={operator} />
          </div>
          <ButtonGroup>
            <Button
              active={!this.state.operator}
              onClick={() => this.setState({operator: undefined})}
            >
              {t('common.filter.nodeModal.executing')}
            </Button>
            <Button
              active={this.state.operator === 'in'}
              onClick={() => this.setState({operator: 'in'})}
            >
              {t('common.filter.nodeModal.executed')}
            </Button>
            <Button
              active={this.state.operator === 'not in'}
              onClick={() => this.setState({operator: 'not in'})}
            >
              {t('common.filter.nodeModal.notExecuted')}
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
          <Button onClick={this.props.close}>{t('common.cancel')}</Button>
          <Button
            variant="primary"
            color="blue"
            disabled={!this.isNodeSelected()}
            onClick={this.createFilter}
          >
            {this.props.filterData ? t('common.filter.editFilter') : t('common.filter.addFilter')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}
