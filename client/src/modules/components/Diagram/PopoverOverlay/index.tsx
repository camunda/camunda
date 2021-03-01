/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Fragment} from 'react';
import CodeModal from 'modules/components/CodeModal';
import {LinkButton} from 'modules/components/LinkButton';

import {compactObject, pickFromObject} from 'modules/utils';

import {beautifyMetadata, getBreadcrumbs} from './service';

import Overlay from '../Overlay';
import * as Styled from './styled';

type Props = {
  metadata?: any;
  selectedFlowNodeId: string;
  selectedFlowNodeName: string;
  onOverlayAdd: (...args: any[]) => any;
  onOverlayClear: (...args: any[]) => any;
  onFlowNodeSelection: (...args: any[]) => any;
  isViewerLoaded: boolean;
  position: {
    top?: number;
    right?: number;
    bottom?: number;
    left?: number;
    side: 'TOP' | 'RIGHT' | 'BOTTOM' | 'LEFT' | 'BOTTOM_MIRROR';
  };
};

type State = any;

export default class PopoverOverlay extends React.Component<Props, State> {
  state = {
    isModalVisible: false,
  };

  handleModalClose = () => {
    this.setState({isModalVisible: false});
  };

  handleModalOpen = () => {
    this.setState({isModalVisible: true});
  };

  renderModal = () => {
    const {metadata, selectedFlowNodeName} = this.props;
    const {isModalVisible} = this.state;

    const headerTitleSuffix = !metadata.isSingleRowPeterCase
      ? ''
      : `Instance ${metadata.data.activityInstanceId}`;

    return (
      <CodeModal
        handleModalClose={this.handleModalClose}
        isModalVisible={isModalVisible}
        headline={`Flow Node "${selectedFlowNodeName}" ${headerTitleSuffix} Metadata`}
        initialValue={beautifyMetadata(this.props.metadata.data)}
        mode="view"
      />
    );
  };

  renderBreadcrumbs = () => {
    const {
      metadata,
      selectedFlowNodeId,
      selectedFlowNodeName,
      onFlowNodeSelection,
    } = this.props;

    const breadcrumbs = getBreadcrumbs({
      metadata,
      selectedFlowNodeName,
      selectedFlowNodeId,
    });

    return breadcrumbs.map((item: any) => {
      return item.hasLink ? (
        <Fragment key={`${selectedFlowNodeId}-a${item.name}`}>
          <LinkButton
            size="small"
            data-testid="select-flownode"
            onClick={() =>
              onFlowNodeSelection(selectedFlowNodeId, item.options)
            }
          >
            {item.name}
          </LinkButton>
          <span> › </span>
        </Fragment>
      ) : (
        <span key={item.name}>{item.name}</span>
      );
    });
  };

  renderSummary = () => {
    const {metadata} = this.props;

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
            {this.renderBreadcrumbs()}
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
        <Styled.Popover side={this.props.position.side} data-testid="popover">
          {this.renderSummary()}
          {Boolean(metadata.data) && (
            <LinkButton
              size="small"
              onClick={this.handleModalOpen}
              title="Show more metadata"
              data-testid="more-metadata"
            >
              More...
            </LinkButton>
          )}
          {this.renderModal()}
        </Styled.Popover>
      </Overlay>
    );
  }
}
