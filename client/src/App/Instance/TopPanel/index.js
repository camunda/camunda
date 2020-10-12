/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect} from 'react';
import PropTypes from 'prop-types';
import {
  getSelectedFlowNodeName,
  getFlowNodeStateOverlays,
  getMultiInstanceBodies,
  getMultiInstanceChildren,
  getCurrentMetadata,
} from './service';

import SpinnerSkeleton from 'modules/components/SpinnerSkeleton';
import Diagram from 'modules/components/Diagram';
import {IncidentsWrapper} from '../IncidentsWrapper';
import {EXPAND_STATE} from 'modules/constants';

import {InstanceHeader} from './InstanceHeader';
import * as Styled from './styled';
import {observer} from 'mobx-react';
import {currentInstance} from 'modules/stores/currentInstance';
import {flowNodeInstance} from 'modules/stores/flowNodeInstance';
import {singleInstanceDiagram} from 'modules/stores/singleInstanceDiagram';
import {sequenceFlows} from 'modules/stores/sequenceFlows';
import {events} from 'modules/stores/events';
const TopPanel = observer((props) => {
  useEffect(() => {
    events.init();
    sequenceFlows.init();

    return () => {
      events.reset();
      sequenceFlows.reset();
    };
  }, []);

  /**
   * Handles selecting a flow node from the diagram
   * @param {string} flowNodeId: id of the selected flow node
   * @param {Object} options: refine, which instances to select
   */
  const handleFlowNodeSelection = async (
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

  const {expandState} = props;

  const {
    state: {selection},
    flowNodeIdToFlowNodeInstanceMap,
    areMultipleNodesSelected,
  } = flowNodeInstance;

  const {items: processedSequenceFlows} = sequenceFlows.state;
  const {instance} = currentInstance.state;

  const {
    nodeMetaDataMap,
    state: {isLoading, isInitialLoadComplete, diagramModel},
  } = singleInstanceDiagram;

  const selectedFlowNodeId = selection?.flowNodeId;
  const metaData = singleInstanceDiagram.getMetaData(selectedFlowNodeId);

  const {items: eventList} = events.state;
  return (
    <Styled.Pane expandState={expandState}>
      <InstanceHeader />
      <Styled.SplitPaneBody data-testid="diagram-panel-body">
        {isLoading && <SpinnerSkeleton data-testid="spinner" />}
        {isInitialLoadComplete && (
          <>
            {instance?.state === 'INCIDENT' && nodeMetaDataMap && (
              <IncidentsWrapper expandState={expandState} />
            )}
            {diagramModel?.definitions && (
              <Diagram
                expandState={expandState}
                onFlowNodeSelection={handleFlowNodeSelection}
                selectableFlowNodes={[
                  ...flowNodeIdToFlowNodeInstanceMap.keys(),
                ]}
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
                  !selection.flowNodeId
                    ? null
                    : getCurrentMetadata(
                        eventList,
                        selectedFlowNodeId,
                        selection?.treeRowIds,
                        flowNodeIdToFlowNodeInstanceMap,
                        areMultipleNodesSelected
                      )
                }
              />
            )}
          </>
        )}
      </Styled.SplitPaneBody>
    </Styled.Pane>
  );
});

TopPanel.propTypes = {
  incidents: PropTypes.object,
  children: PropTypes.node,
  expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
};

export {TopPanel};
