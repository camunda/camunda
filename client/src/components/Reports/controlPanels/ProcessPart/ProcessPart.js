/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Button, Modal, BPMNDiagram, ClickBehavior, ActionItem, Message} from 'components';

import PartHighlight from './PartHighlight';

import './ProcessPart.scss';

export default class ProcessPart extends React.Component {
  state = {
    modalOpen: false,
    start: null,
    end: null,
    hasPath: true
  };

  render() {
    return (
      <React.Fragment>
        {this.renderButton()}
        {this.renderPart()}
        {this.renderModal()}
      </React.Fragment>
    );
  }

  renderButton() {
    if (!this.props.processPart) {
      return <Button onClick={this.openModal}>Process Instance Part</Button>;
    }
  }

  renderFlowNodeName = id => {
    return this.props.flowNodeNames ? this.props.flowNodeNames[id] || id : id;
  };

  renderPart() {
    if (this.props.processPart) {
      return (
        <div onClick={this.openModal} className="ProcessPart__current">
          <ActionItem
            onClick={evt => {
              evt.stopPropagation();
              this.props.update(null);
            }}
          >
            Only regard part between{' '}
            <span className="FilterList__value">
              {this.renderFlowNodeName(this.props.processPart.start)}
            </span>
            <span> and </span>
            <span className="FilterList__value">
              {this.renderFlowNodeName(this.props.processPart.end)}
            </span>
          </ActionItem>
        </div>
      );
    }
  }

  setHasPath = hasPath => {
    if (this.state.hasPath !== hasPath) {
      // this is called during render of PartHighlight. We cannot update state during a render, so we do it later
      window.setTimeout(() => this.setState({hasPath}));
    }
  };

  renderModal() {
    const {start, end, hasPath, modalOpen} = this.state;

    const selection = [start, end].filter(v => v);
    return (
      <Modal
        open={modalOpen}
        onClose={this.closeModal}
        onConfirm={this.isValid() ? this.applyPart : undefined}
        size="max"
        className="ProcessPartModal"
      >
        <Modal.Header>Set Process Instance Part</Modal.Header>
        <Modal.Content>
          <span>
            Only regard the process instance part between{' '}
            <ActionItem
              disabled={!start}
              onClick={() => this.setState({start: null, hasPath: true})}
            >
              {start ? start.name || start.id : 'Please select start'}
            </ActionItem>{' '}
            and{' '}
            <ActionItem disabled={!end} onClick={() => this.setState({end: null, hasPath: true})}>
              {end ? end.name || end.id : 'Please select end'}
            </ActionItem>
          </span>
          {start && end && !hasPath && (
            <Message type="warning">
              You selected two nodes, but there is no obvious connection between those nodes. Report
              results may be empty or misleading.
            </Message>
          )}
          <div className="diagram-container">
            <BPMNDiagram xml={this.props.xml}>
              <ClickBehavior
                setSelectedNodes={this.setSelectedNodes}
                onClick={this.selectNode}
                selectedNodes={selection}
              />
              <PartHighlight nodes={selection} setHasPath={this.setHasPath} />
            </BPMNDiagram>
          </div>
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.closeModal}>Cancel</Button>
          <Button type="primary" color="blue" onClick={this.applyPart} disabled={!this.isValid()}>
            Apply
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }

  openModal = () => this.setState({modalOpen: true, ...this.props.processPart});
  closeModal = () => this.setState({modalOpen: false, start: null, end: null, hasPath: true});

  setSelectedNodes = ([start, end]) => {
    this.setState({start, end});
  };

  selectNode = node => {
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
