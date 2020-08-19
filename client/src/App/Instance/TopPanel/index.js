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
  getFlowNodeStateOverlays,
  getMultiInstanceBodies,
  getMultiInstanceChildren,
} from './service';

import SpinnerSkeleton from 'modules/components/SpinnerSkeleton';

import Diagram from 'modules/components/Diagram';
import IncidentsWrapper from '../IncidentsWrapper';
import {EXPAND_STATE} from 'modules/constants';

import InstanceHeader from './InstanceHeader';
import * as Styled from './styled';
import {observer} from 'mobx-react';
import {currentInstance} from 'modules/stores/currentInstance';
import {flowNodeInstance} from 'modules/stores/flowNodeInstance';
import {singleInstanceDiagram} from 'modules/stores/singleInstanceDiagram';
import {sequenceFlows} from 'modules/stores/sequenceFlows';

const TopPanel = observer(
  class TopPanel extends React.PureComponent {
    static propTypes = {
      incidents: PropTypes.object,
      children: PropTypes.node,
      onInstanceOperation: PropTypes.func,
      expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
      onTreeRowSelection: PropTypes.func,
      getCurrentMetadata: PropTypes.func,
    };

    componentDidMount() {
      sequenceFlows.init();
    }

    componentWillUnmount() {
      sequenceFlows.reset();
    }

    addFlowNodeNames = (incidents) =>
      incidents.map((incident) => this.addFlowNodeName(incident));

    addFlowNodeName = (object) => {
      const modifiedObject = {...object};
      const nodeMetaData = singleInstanceDiagram.getMetaData(
        modifiedObject.flowNodeId
      );

      modifiedObject.flowNodeName =
        (nodeMetaData && nodeMetaData.name) || object.flowNodeId;
      return modifiedObject;
    };

    /**
     * Handles selecting a flow node from the diagram
     * @param {string} flowNodeId: id of the selected flow node
     * @param {Object} options: refine, which instances to select
     */
    handleFlowNodeSelection = async (
      flowNodeId,
      options = {
        selectMultiInstanceChildrenOnly: false,
      }
    ) => {
      const {flowNodeIdToFlowNodeInstanceMap} = flowNodeInstance;
      const {instance} = currentInstance.state;
      let treeRowIds = [instance.id];
      if (flowNodeId) {
        const flowNodeInstancesMap = flowNodeIdToFlowNodeInstanceMap.get(
          flowNodeId
        );

        treeRowIds = options.selectMultiInstanceChildrenOnly
          ? getMultiInstanceChildren(flowNodeInstancesMap)
          : getMultiInstanceBodies(flowNodeInstancesMap);
      }

      flowNodeInstance.setCurrentSelection({flowNodeId, treeRowIds});
    };

    renderContent() {
      const {incidents, onInstanceOperation, ...props} = this.props;

      const {
        state: {selection},
        flowNodeIdToFlowNodeInstanceMap,
      } = flowNodeInstance;

      const {items: processedSequenceFlows} = sequenceFlows.state;
      const {instance} = currentInstance.state;
      const {
        nodeMetaDataMap,
        state: {diagramModel},
      } = singleInstanceDiagram;

      const selectedFlowNodeId = selection?.flowNodeId;
      const metaData = singleInstanceDiagram.getMetaData(selectedFlowNodeId);
      return (
        <>
          {instance.state === 'INCIDENT' && nodeMetaDataMap && (
            <IncidentsWrapper
              expandState={props.expandState}
              incidents={this.addFlowNodeNames(incidents.incidents)}
              incidentsCount={incidents.count}
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
          {diagramModel?.definitions && (
            <Diagram
              expandState={props.expandState}
              onFlowNodeSelection={this.handleFlowNodeSelection}
              selectableFlowNodes={[...flowNodeIdToFlowNodeInstanceMap.keys()]}
              processedSequenceFlows={processedSequenceFlows}
              selectedFlowNodeId={selection.flowNodeId}
              selectedFlowNodeName={getSelectedFlowNodeName(
                selectedFlowNodeId,
                metaData
              )}
              flowNodeStateOverlays={getFlowNodeStateOverlays(
                flowNodeIdToFlowNodeInstanceMap
              )}
              definitions={diagramModel.definitions}
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
        onInstanceOperation,
        incidents,
        onTreeRowSelection,
        getCurrentMetadata,
        ...props
      } = this.props;

      const {isLoading, isInitialLoadComplete} = singleInstanceDiagram.state;
      return (
        <Styled.Pane {...props}>
          <InstanceHeader
            incidents={incidents}
            onInstanceOperation={onInstanceOperation}
          />
          <Styled.SplitPaneBody data-test="diagram-panel-body">
            {isLoading && <SpinnerSkeleton data-test="spinner" />}
            {isInitialLoadComplete && this.renderContent()}
          </Styled.SplitPaneBody>
        </Styled.Pane>
      );
    }
  }
);

export {TopPanel};
