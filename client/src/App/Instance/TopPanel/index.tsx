/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect} from 'react';
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

import {InstanceHeader} from './InstanceHeader';
import * as Styled from './styled';
import {observer} from 'mobx-react';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {sequenceFlowsStore} from 'modules/stores/sequenceFlows';
import {eventsStore} from 'modules/stores/events';
import {StatusMessage} from 'modules/components/StatusMessage';

type Props = {
  incidents?: unknown;
  children?: React.ReactNode;
  expandState?: 'DEFAULT' | 'EXPANDED' | 'COLLAPSED';
};

const TopPanel: React.FC<Props> = observer((props) => {
  useEffect(() => {
    eventsStore.init();
    sequenceFlowsStore.init();

    return () => {
      eventsStore.reset();
      sequenceFlowsStore.reset();
    };
  }, []);

  /**
   * Handles selecting a flow node from the diagram
   * @param {string} flowNodeId: id of the selected flow node
   * @param {Object} options: refine, which instances to select
   */
  const handleFlowNodeSelection = async (
    flowNodeId: any,
    options = {
      selectMultiInstanceChildrenOnly: false,
    }
  ) => {
    const {flowNodeIdToFlowNodeInstanceMap} = flowNodeInstanceStore;
    const {instance} = currentInstanceStore.state;
    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
    let treeRowIds = [instance.id];
    if (flowNodeId) {
      const flowNodeInstancesMap = flowNodeIdToFlowNodeInstanceMap.get(
        flowNodeId
      );

      treeRowIds = options.selectMultiInstanceChildrenOnly
        ? getMultiInstanceChildren(flowNodeInstancesMap)
        : getMultiInstanceBodies(flowNodeInstancesMap);
    }

    flowNodeInstanceStore.setCurrentSelection({flowNodeId, treeRowIds});
  };

  const {expandState} = props;

  const {
    state: {selection},
    flowNodeIdToFlowNodeInstanceMap,
    areMultipleNodesSelected,
  } = flowNodeInstanceStore;

  const {items: processedSequenceFlows} = sequenceFlowsStore.state;
  const {instance} = currentInstanceStore.state;

  const {
    nodeMetaDataMap,
    state: {status, diagramModel},
  } = singleInstanceDiagramStore;

  const selectedFlowNodeId = selection?.flowNodeId;
  const metaData = singleInstanceDiagramStore.getMetaData(selectedFlowNodeId);

  const {items: eventList} = eventsStore.state;
  return (
    <Styled.Pane expandState={expandState}>
      <InstanceHeader />
      <Styled.SplitPaneBody data-testid="diagram-panel-body">
        {['first-fetch', 'fetching'].includes(status) && (
          <SpinnerSkeleton data-testid="spinner" />
        )}
        {status === 'error' && (
          <StatusMessage variant="error">
            Diagram could not be fetched
          </StatusMessage>
        )}
        {status === 'fetched' && (
          <>
            {instance?.state === 'INCIDENT' && nodeMetaDataMap && (
              <IncidentsWrapper expandState={expandState} />
            )}
            {/* @ts-expect-error ts-migrate(2339) FIXME: Property 'definitions' does not exist on type 'nev... Remove this comment to see the full error message */}
            {diagramModel?.definitions && (
              <Diagram
                expandState={expandState}
                onFlowNodeSelection={handleFlowNodeSelection}
                selectableFlowNodes={[
                  ...flowNodeIdToFlowNodeInstanceMap.keys(),
                ]}
                processedSequenceFlows={processedSequenceFlows}
                // @ts-expect-error ts-migrate(2322) FIXME: Type 'null' is not assignable to type 'string | un... Remove this comment to see the full error message
                selectedFlowNodeId={selection.flowNodeId}
                selectedFlowNodeName={getSelectedFlowNodeName(
                  selectedFlowNodeId,
                  metaData
                )}
                flowNodeStateOverlays={getFlowNodeStateOverlays(
                  flowNodeIdToFlowNodeInstanceMap
                )}
                // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
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

export {TopPanel};
