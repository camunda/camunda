/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {
  mapify,
  getSelectedFlowNodeName,
  getFlowNodeStateOverlays
} from './service';

import {withData} from 'modules/DataManager';
import SpinnerSkeleton from 'modules/components/Skeletons';

import Diagram from 'modules/components/Diagram';
import IncidentsWrapper from '../IncidentsWrapper';
import {EXPAND_STATE, LOADING_STATE, UNNAMED_ACTIVITY} from 'modules/constants';

import InstanceHeader from './InstanceHeader';
import * as Styled from './styled';

class TopPanel extends React.PureComponent {
  static propTypes = {
    instance: PropTypes.shape({
      id: PropTypes.string.isRequired,
      startDate: PropTypes.string.isRequired,
      endDate: PropTypes.string,
      state: PropTypes.string.isRequired,
      errorMessage: PropTypes.string,
      workflowVersion: PropTypes.number
    }).isRequired,
    children: PropTypes.node,
    forceInstanceSpinner: PropTypes.bool,
    onInstanceOperation: PropTypes.func,
    expandState: PropTypes.oneOf(Object.values(EXPAND_STATE))
  };

  constructor(props) {
    super(props);
    this.state = {
      isLoading: LOADING_STATE.LOADING
    };
    this.subscriptions = {
      LOAD_STATE_DEFINITIONS: ({state}) => {
        if (state === LOADING_STATE.LOADED) {
          this.setState({isLoading: state});
        }
      }
    };
  }

  componentDidMount() {
    this.props.dataManager.subscribe(this.subscriptions);
  }

  componentWillUnmount() {
    this.props.dataManager.unsubscribe(this.subscriptions);
  }

  addFLowNodeNames = incidents =>
    incidents.map(incident => this.addFlowNodeName(incident));

  addFlowNodeName = object => {
    const modifiedObject = {...object};
    const nodeMetaData = this.props.nodeMetaDataMap.get(
      modifiedObject.flowNodeId
    );

    modifiedObject.flowNodeName =
      (nodeMetaData && nodeMetaData.name) || UNNAMED_ACTIVITY;
    return modifiedObject;
  };

  renderContent() {
    const {
      instance,
      incidents,
      onInstanceOperation,
      selection,
      diagramDefinitions,
      activityIdToActivityInstanceMap,
      nodeMetaDataMap,
      ...props
    } = this.props;

    return (
      <>
        {instance.state === 'INCIDENT' && nodeMetaDataMap && (
          <IncidentsWrapper
            expandState={props.expandState}
            incidents={this.addFLowNodeNames(incidents.incidents)}
            incidentsCount={incidents.count}
            instance={instance}
            selectedFlowNodeInstanceIds={selection.treeRowIds}
            onIncidentSelection={this.props.onTreeRowSelection}
            errorTypes={mapify(incidents.errorTypes, 'errorType')}
            flowNodes={mapify(
              incidents.flowNodes,
              'flowNodeId',
              this.addFlowNodeName
            )}
          />
        )}
        {diagramDefinitions && activityIdToActivityInstanceMap && (
          <Diagram
            expandState={props.expandState}
            onFlowNodeSelection={this.props.onFlowNodeSelection}
            selectableFlowNodes={[...activityIdToActivityInstanceMap.keys()]}
            selectedFlowNodeId={selection.flowNodeId}
            selectedFlowNodeName={getSelectedFlowNodeName(
              selection,
              nodeMetaDataMap
            )}
            flowNodeStateOverlays={getFlowNodeStateOverlays(
              activityIdToActivityInstanceMap
            )}
            definitions={diagramDefinitions}
            metadata={
              !selection.flowNodeId ? null : this.props.getCurrentMetadata()
            }
          />
        )}
      </>
    );
  }

  render() {
    const {
      instance,
      onInstanceOperation,
      incidents,
      selection,
      diagramDefinitions,
      activityIdToActivityInstanceMap,
      nodeMetaDataMap,
      onTreeRowSelection,
      onFlowNodeSelection,
      getCurrentMetadata,

      ...props
    } = this.props;

    return (
      <Styled.Pane {...props}>
        <InstanceHeader
          instance={instance}
          incidents={incidents}
          onInstanceOperation={onInstanceOperation}
        />
        <Styled.SplitPaneBody data-test="diagram-panel-body">
          {this.state.isLoading === LOADING_STATE.LOADING && (
            <SpinnerSkeleton data-test="spinner" />
          )}
          {this.state.isLoading === LOADING_STATE.LOADED &&
            this.renderContent()}
        </Styled.SplitPaneBody>
      </Styled.Pane>
    );
  }
}

const WrappedPanel = withData(TopPanel);
WrappedPanel.WrappedComponent = TopPanel;

export default WrappedPanel;
