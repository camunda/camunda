/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import CodeModal from 'modules/components/CodeModal';
import {LinkButton} from 'modules/components/LinkButton';
import {Fragment, useState} from 'react';
import {Overlay} from '../Overlay';
import {
  PopoverOverlayStyle,
  Popover,
  SummaryDataKey,
  SummaryDataValue,
  PeterCaseSummaryHeader,
  PeterCaseSummaryBody,
  SummaryHeader,
  SummaryData,
} from './styled';
import {
  flowNodeMetaDataStore,
  InstanceMetaDataEntity,
} from 'modules/stores/flowNodeMetaData';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {observer} from 'mobx-react';
import {OverlayType} from 'modules/types/modeler';
import {beautifyMetadata} from './beautifyMetadata';
import {getModalHeadline} from './getModalHeadline';
import {getPopoverPosition} from './getPopoverPosition';
import {InstanceAnchor} from 'App/Instances/ListPanel/List/Instances/styled';
import {Locations} from 'modules/routes';

const InstanceMetaData: React.FC<{
  metaData: InstanceMetaDataEntity;
}> = ({
  metaData: {
    flowNodeInstanceId,
    jobId,
    startDate,
    endDate,
    incidentErrorType,
    incidentErrorMessage,
    calledProcessInstanceId,
  },
}) => {
  return (
    <SummaryData>
      <SummaryDataKey>flowNodeInstanceId:</SummaryDataKey>
      <SummaryDataValue>{flowNodeInstanceId}</SummaryDataValue>
      {jobId !== null && (
        <>
          <SummaryDataKey>jobId:</SummaryDataKey>
          <SummaryDataValue>{jobId}</SummaryDataValue>
        </>
      )}
      <SummaryDataKey>startDate:</SummaryDataKey>
      <SummaryDataValue>{startDate}</SummaryDataValue>
      <SummaryDataKey>endDate:</SummaryDataKey>
      <SummaryDataValue>{endDate || '--'}</SummaryDataValue>
      {incidentErrorType !== null && (
        <>
          <SummaryDataKey>incidentErrorType:</SummaryDataKey>
          <SummaryDataValue>{incidentErrorType}</SummaryDataValue>
        </>
      )}
      {incidentErrorMessage !== null && (
        <>
          <SummaryDataKey>incidentErrorMessage:</SummaryDataKey>
          <SummaryDataValue>{incidentErrorMessage}</SummaryDataValue>
        </>
      )}
      <SummaryDataKey>calledProcessInstanceId:</SummaryDataKey>
      <SummaryDataValue>
        {calledProcessInstanceId ? (
          <InstanceAnchor
            to={(location) =>
              Locations.instance(calledProcessInstanceId, location)
            }
            title={`View called instance ${calledProcessInstanceId}`}
          >
            {calledProcessInstanceId}
          </InstanceAnchor>
        ) : (
          'None'
        )}
      </SummaryDataValue>
    </SummaryData>
  );
};

type PopoverOverlayProps = {
  onOverlayAdd: (
    id: string,
    type: string,
    overlay: {position: OverlayType['position']; html: HTMLDivElement}
  ) => void;
  onOverlayClear: ({element}: {element: HTMLDivElement}) => void;
  isViewerLoaded: boolean;
  diagramContainer: HTMLElement;
  flowNode: SVGGraphicsElement;
};

const PopoverOverlay = observer(
  ({
    onOverlayAdd,
    onOverlayClear,
    isViewerLoaded,
    diagramContainer,
    flowNode,
  }: PopoverOverlayProps) => {
    const [isModalVisible, setIsModalVisible] = useState(false);
    const flowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;
    const {metaData} = flowNodeMetaDataStore.state;

    if (flowNodeId !== undefined && metaData !== null) {
      const flowNodeMetaData = singleInstanceDiagramStore.getMetaData(
        flowNodeId || null
      );
      const flowNodeName = flowNodeMetaData?.name || flowNodeId;
      const position = getPopoverPosition(
        {diagramContainer, flowNode},
        flowNodeMetaDataStore.hasMultipleInstances
      );
      return (
        <Overlay
          id={flowNodeId}
          onOverlayAdd={onOverlayAdd}
          onOverlayClear={onOverlayClear}
          isViewerLoaded={isViewerLoaded}
          position={position.overlay}
          type="popover"
        >
          <PopoverOverlayStyle side={position.side} />
          {metaData !== null && (
            <Popover side={position.side} data-testid="popover">
              <div>
                {metaData.instanceCount !== null && metaData.instanceCount > 1 && (
                  <>
                    <PeterCaseSummaryHeader>
                      {`There are ${metaData.instanceCount} instances`}
                    </PeterCaseSummaryHeader>
                    <PeterCaseSummaryBody>
                      To view metadata for any of these, select one instance in
                      the Instance History.
                    </PeterCaseSummaryBody>
                  </>
                )}
                {metaData.instanceMetadata !== null && (
                  <>
                    {metaData.breadcrumb.length > 0 && (
                      <SummaryHeader>
                        {metaData.breadcrumb.map((item) => (
                          <Fragment key={`${flowNodeId}-${item.flowNodeType}`}>
                            <LinkButton
                              size="small"
                              data-testid="select-flownode"
                              onClick={() =>
                                flowNodeSelectionStore.selectFlowNode({
                                  flowNodeId,
                                  flowNodeType: item.flowNodeType,
                                  isMultiInstance:
                                    item.flowNodeType === 'MULTI_INSTANCE_BODY',
                                })
                              }
                            >
                              {flowNodeName}
                              {item.flowNodeType === 'MULTI_INSTANCE_BODY'
                                ? ' (Multi Instance)'
                                : ''}
                            </LinkButton>
                            {' › '}
                          </Fragment>
                        ))}
                        {metaData.flowNodeInstanceId && (
                          <span>{metaData.flowNodeInstanceId}</span>
                        )}
                      </SummaryHeader>
                    )}
                    <InstanceMetaData metaData={metaData.instanceMetadata} />
                  </>
                )}
              </div>
              {metaData.instanceMetadata && (
                <LinkButton
                  size="small"
                  onClick={() => setIsModalVisible(true)}
                  title="Show more metadata"
                  data-testid="more-metadata"
                >
                  More...
                </LinkButton>
              )}
              <CodeModal
                handleModalClose={() => setIsModalVisible(false)}
                isModalVisible={isModalVisible}
                headline={getModalHeadline({flowNodeName, metaData})}
                initialValue={beautifyMetadata(metaData.instanceMetadata)}
                mode="view"
              />
            </Popover>
          )}
        </Overlay>
      );
    } else {
      return null;
    }
  }
);

export {PopoverOverlay};
