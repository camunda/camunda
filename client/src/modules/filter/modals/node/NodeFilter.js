/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {Modal, ButtonGroup, Button, BPMNDiagram, ClickBehavior} from 'components';
import {t} from 'translation';

import NodeListPreview from './NodeListPreview';

import './NodeFilter.scss';

export default class NodeFilter extends React.Component {
  constructor(props) {
    super(props);

    const {filterData} = this.props;

    this.state = {
      selectedNodes: filterData?.data.values ?? [],
      operator: filterData?.data ? filterData?.data.operator : 'in',
      type: filterData?.type ?? 'executedFlowNodes',
    };
  }

  toggleNode = (toggledNode) => {
    if (this.state.selectedNodes.includes(toggledNode)) {
      this.setState({
        selectedNodes: this.state.selectedNodes.filter((node) => node !== toggledNode),
      });
    } else {
      this.setState({
        selectedNodes: this.state.selectedNodes.concat([toggledNode]),
      });
    }
  };

  createFilter = () => {
    const values = this.state.selectedNodes.map((node) => node.id);
    const {operator, type} = this.state;
    this.props.addFilter({type, data: {operator, values}});
  };

  isNodeSelected = () => {
    return this.state.selectedNodes.length > 0;
  };

  setSelectedNodes = (nodes) => {
    this.setState({
      selectedNodes: nodes,
    });
  };

  render() {
    const {selectedNodes, operator, type} = this.state;
    return (
      <Modal
        open
        onClose={this.props.close}
        onConfirm={this.isNodeSelected() ? this.createFilter : undefined}
        className="NodeFilter"
        size="max"
      >
        <Modal.Header>
          {t('common.filter.modalHeader', {
            type: t(`common.filter.types.flowNode`),
          })}
        </Modal.Header>
        <Modal.Content className="modalContent">
          <div className="preview">
            <span>{t('common.filter.nodeModal.previewLabel')}</span>{' '}
            <NodeListPreview nodes={selectedNodes} operator={operator} type={type} />
          </div>
          <p className={classnames('note', {hidden: operator !== 'in'})}>
            {t('common.filter.nodeModal.note')}
          </p>
          <ButtonGroup>
            <Button
              active={type === 'executingFlowNodes'}
              onClick={() => this.setState({operator: undefined, type: 'executingFlowNodes'})}
            >
              {t('common.filter.nodeModal.executing')}
            </Button>
            <Button
              active={operator === 'in'}
              onClick={() => this.setState({operator: 'in', type: 'executedFlowNodes'})}
            >
              {t('common.filter.nodeModal.executed')}
            </Button>
            <Button
              active={operator === 'not in'}
              onClick={() => this.setState({operator: 'not in', type: 'executedFlowNodes'})}
            >
              {t('common.filter.nodeModal.notExecuted')}
            </Button>
            <Button
              active={type === 'canceledFlowNodes'}
              onClick={() => this.setState({operator: undefined, type: 'canceledFlowNodes'})}
            >
              {t('common.filter.nodeModal.canceled')}
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
          <Button main onClick={this.props.close}>
            {t('common.cancel')}
          </Button>
          <Button main primary disabled={!this.isNodeSelected()} onClick={this.createFilter}>
            {this.props.filterData ? t('common.filter.editFilter') : t('common.filter.addFilter')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}
