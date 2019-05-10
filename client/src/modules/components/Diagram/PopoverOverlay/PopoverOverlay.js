/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {compactObject, pickFromObject} from 'modules/utils';
import Modal from 'modules/components/Modal';

import Overlay from '../Overlay';
import * as Styled from './styled';

export default class PopoverOverlay extends React.Component {
  state = {
    isModalVisibile: false
  };

  handleModalClose = () => {
    this.setState({isModalVisibile: false});
  };

  handleModalOpen = () => {
    this.setState({isModalVisibile: true});
  };

  renderBeautifiedMetadata = () => {
    return JSON.stringify(this.props.metadata.data, null, '\t')
      .replace(/\\n/g, '\n\t\t')
      .replace(/\\t/g, '\t')
      .split('\n')
      .map((line, idx) => <Styled.CodeLine key={idx}>{line}</Styled.CodeLine>);
  };

  renderModal = () => {
    const {metadata, selectedFlowNodeName} = this.props;
    const {isModalVisibile} = this.state;

    const headerTitleSuffix = !metadata.isSingleRowPeterCase
      ? ''
      : `Instance ${metadata.data.activityInstanceId}`;

    return (
      <Modal onModalClose={this.handleModalClose} isVisible={isModalVisibile}>
        <Modal.Header>{`Flow Node "${selectedFlowNodeName}" ${headerTitleSuffix} Metadata`}</Modal.Header>
        <Styled.ModalBody>
          <pre>
            <Styled.LinesSeparator />
            <code>
              {this.props.metadata.data && this.renderBeautifiedMetadata()}
            </code>
          </pre>
        </Styled.ModalBody>
        <Modal.Footer>
          <Modal.PrimaryButton
            title="Close Modal"
            onClick={this.handleModalClose}
          >
            Close
          </Modal.PrimaryButton>
        </Modal.Footer>
      </Modal>
    );
  };

  renderSummary = () => {
    const {
      metadata,
      selectedFlowNodeId,
      selectedFlowNodeName,
      onFlowNodeSelection
    } = this.props;

    if (metadata.isMultiRowPeterCase) {
      return (
        <>
          <Styled.PeterCaseSummaryHeader>
            {`There are ${metadata.instancesCount} instances`}
          </Styled.PeterCaseSummaryHeader>
          <Styled.PeterCaseSummaryBody>
            To view metadata for any of these, select one instance in the
            Instance History.
          </Styled.PeterCaseSummaryBody>
        </>
      );
    }

    const summaryKeys = ['activityInstanceId', 'jobId', 'startDate', 'endDate'];
    const summary = compactObject(pickFromObject(metadata.data, summaryKeys));
    return (
      <>
        {metadata.isSingleRowPeterCase && (
          <Styled.SummaryHeader>
            <Styled.Button
              data-test="select-flownode"
              onClick={() => onFlowNodeSelection(selectedFlowNodeId)}
            >
              {selectedFlowNodeName}
            </Styled.Button>
            <span> › {metadata.data.activityInstanceId}</span>
          </Styled.SummaryHeader>
        )}
        <Styled.SummaryData>
          {Object.entries(summary).map(([key, value]) => {
            return (
              <React.Fragment key={key}>
                <Styled.SummaryDataKey>{key}:</Styled.SummaryDataKey>
                <Styled.SummaryDataValue>
                  {typeof value === 'string' ? value : JSON.stringify(value)}
                </Styled.SummaryDataValue>
              </React.Fragment>
            );
          })}
        </Styled.SummaryData>
      </>
    );
  };

  render() {
    const {
      metadata,
      onOverlayAdd,
      onOverlayClear,
      isViewerLoaded,
      selectedFlowNodeId,
      theme
    } = this.props;

    return (
      <Overlay
        onOverlayAdd={onOverlayAdd}
        onOverlayClear={onOverlayClear}
        isViewerLoaded={isViewerLoaded}
        id={selectedFlowNodeId}
        type={'popover'}
        position={this.props.position}
      >
        <Styled.PopoverOverlayStyle side={this.props.position.side} />
        <Styled.Popover theme={theme} side={this.props.position.side}>
          {this.renderSummary()}
          {Boolean(metadata.data) && (
            <Styled.Button
              onClick={this.handleModalOpen}
              title="Show more metadata"
              data-test="more-metadata"
            >
              More...
            </Styled.Button>
          )}
          {this.renderModal()}
        </Styled.Popover>
      </Overlay>
    );
  }
}

PopoverOverlay.propTypes = {
  metadata: PropTypes.object,
  selectedFlowNodeId: PropTypes.string.isRequired,
  selectedFlowNodeName: PropTypes.string.isRequired,
  onOverlayAdd: PropTypes.func.isRequired,
  onOverlayClear: PropTypes.func.isRequired,
  onFlowNodeSelection: PropTypes.func.isRequired,
  isViewerLoaded: PropTypes.bool.isRequired,
  theme: PropTypes.oneOf(['dark', 'light']).isRequired,
  position: PropTypes.shape({
    top: PropTypes.number,
    right: PropTypes.number,
    bottom: PropTypes.number,
    left: PropTypes.number,
    side: PropTypes.oneOf(['TOP', 'RIGHT', 'BOTTOM', 'LEFT', 'BOTTOM_MIRROR'])
      .isRequired
  }).isRequired
};
