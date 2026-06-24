/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {Button, InlineNotification, Tag} from '@carbon/react';

import {
  Modal,
  BPMNDiagram,
  ClickBehavior,
  PartHighlight,
  ActionItem,
  SelectionPreview,
} from 'components';
import {t} from 'translation';

import './ProcessPart.scss';

export default class ProcessPart extends React.Component {
  state = {
    modalOpen: false,
    start: null,
    end: null,
    hasPath: true,
    hoveredNode: null,
  };

  render() {
    return (
      <div className="ProcessPart">
        {this.renderButton()}
        {this.renderPart()}
        {this.renderModal()}
      </div>
    );
  }

  renderButton() {
    if (!this.props.processPart) {
      return (
        <Button size="sm" kind="tertiary" onClick={this.openModal}>
          {t('report.processPart.label')}
        </Button>
      );
    }
  }

  renderFlowNodeName = (id) => {
    return this.props.flowNodeNames ? this.props.flowNodeNames[id] || id : id;
  };

  renderPart() {
    if (this.props.processPart) {
      return (
        <div className="ProcessPart__current">
          <ActionItem
            onClick={(evt) => {
              evt.stopPropagation();
              this.props.update(null);
            }}
            onEdit={this.openModal}
          >
            <Tag size="sm" type="blue">
              {t('report.processPart.label')}
            </Tag>
            <div>
              <b>{this.renderFlowNodeName(this.props.processPart.start)}</b>
              <span> {t('common.and')} </span>
              <b>{this.renderFlowNodeName(this.props.processPart.end)}</b>
            </div>
          </ActionItem>
        </div>
      );
    }
  }

  setHasPath = (hasPath) => {
    if (this.state.hasPath !== hasPath) {
      // this is called during render of PartHighlight. We cannot update state during a render, so we do it later
      window.setTimeout(() => this.setState({hasPath}));
    }
  };

  renderModal() {
    const {start, end, hasPath, modalOpen, hoveredNode} = this.state;

    const selection = [start, end].filter((v) => v);
    return (
      <Modal open={modalOpen} onClose={this.closeModal} size="lg" className="ProcessPartModal">
        <Modal.Header title={t('report.processPart.title')} />
        <Modal.Content>
          {start && end && !hasPath && (
            <InlineNotification
              className="notification"
              kind="warning"
              hideCloseButton
              subtitle={t('report.processPart.noPathWarning')}
            />
          )}
          <span>
            {t('report.processPart.description')}{' '}
            <SelectionPreview
              disabled={!start}
              highlighted={hoveredNode && (start ? hoveredNode.id === start.id : true)}
              onClick={() => this.setState({start: null, hasPath: true})}
            >
              {start ? start.name || start.id : t('report.processPart.selectStart')}
            </SelectionPreview>
            <span> {t('common.and')} </span>
            <SelectionPreview
              highlighted={
                hoveredNode &&
                start &&
                (end ? hoveredNode.id === end.id : hoveredNode.id !== start.id)
              }
              disabled={!end}
              onClick={() => this.setState({end: null, hasPath: true})}
            >
              {end ? end.name || end.id : t('report.processPart.selectEnd')}
            </SelectionPreview>
          </span>
          <div className="diagram-container">
            <BPMNDiagram xml={this.props.xml}>
              <ClickBehavior
                setSelectedNodes={this.setSelectedNodes}
                onClick={this.selectNode}
                onHover={(hoveredNode) => this.setState({hoveredNode})}
                selectedNodes={selection}
              />
              <PartHighlight nodes={selection} setHasPath={this.setHasPath} />
            </BPMNDiagram>
          </div>
        </Modal.Content>
        <Modal.Footer>
          <Button kind="secondary" onClick={this.closeModal}>
            {t('common.cancel')}
          </Button>
          <Button onClick={this.applyPart} disabled={!this.isValid()}>
            {t('common.apply')}
          </Button>
        </Modal.Footer>
      </Modal>
    );
  }

  openModal = () => this.setState({modalOpen: true, ...this.props.processPart});
  closeModal = () => this.setState({modalOpen: false, start: null, end: null, hasPath: true});

  setSelectedNodes = ([start, end]) => {
    this.setState({start, end});
  };

  selectNode = (node) => {
    if (this.state.start === node) {
      return this.setState({start: null, hasPath: true});
    }
    if (this.state.end === node) {
      return this.setState({end: null, hasPath: true});
    }
    if (!this.state.start) {
      return this.setState({start: node});
    } else {
      return this.setState({end: node});
    }
  };

  applyPart = () => {
    this.props.update({start: this.state.start.id, end: this.state.end.id});
    this.closeModal();
  };

  isValid = () => this.state.start && this.state.end;
}
