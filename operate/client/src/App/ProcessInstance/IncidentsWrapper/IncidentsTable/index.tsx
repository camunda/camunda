/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IncidentOperation} from 'modules/components/IncidentOperation';
import {formatDate} from 'modules/utils/date';
import {getSortParams} from 'modules/utils/filter';
import {sortIncidents} from './service';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {observer} from 'mobx-react';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';
import {FlexContainer, ErrorMessageCell} from './styled';
import {Incident, incidentsStore} from 'modules/stores/incidents';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/Routes';
import {useLocation} from 'react-router-dom';
import {tracking} from 'modules/tracking';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {Button} from '@carbon/react';
import {SortableTable} from 'modules/components/SortableTable';
import {useState} from 'react';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';

const IncidentsTable: React.FC = observer(function IncidentsTable() {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [modalContent, setModalContent] = useState<string>('');
  const [modalTitle, setModalTitle] = useState<string>('');

  const {processInstanceId = ''} = useProcessInstancePageParams();
  const location = useLocation();
  const {sortBy, sortOrder} = getSortParams(location.search) || {
    sortBy: 'creationTime',
    sortOrder: 'desc',
  };

  const {filteredIncidents} = incidentsStore;

  const handleModalClose = () => {
    setIsModalVisible(false);
    setModalContent('');
    setModalTitle('');
  };

  const handleMoreButtonClick = (
    errorMessage: string,
    flowNodeName: string,
  ) => {
    setIsModalVisible(true);
    setModalContent(errorMessage);
    setModalTitle(`Flow Node "${flowNodeName}" Error`);
  };

  const sortedIncidents: Incident[] = sortIncidents(
    filteredIncidents,
    sortBy,
    sortOrder,
  );

  const isJobIdPresent = sortedIncidents.some(({jobId}) => jobId !== null);
  const hasPermissionForRetryOperation =
    processInstanceDetailsStore.hasPermission(['UPDATE_PROCESS_INSTANCE']);

  const hasIncidentInCalledInstance = sortedIncidents.some(
    ({rootCauseInstance}) =>
      rootCauseInstance?.instanceId !== processInstanceId,
  );

  return (
    <>
      <SortableTable
        state="content"
        selectionType="row"
        columnsWithNoContentPadding={['operations', 'errorMessage']}
        onSelect={(rowId) => {
          const incident = sortedIncidents.find(({id}) => id === rowId);
          if (incident === undefined) {
            return;
          }

          incidentsStore.isSingleIncidentSelected(incident.flowNodeInstanceId)
            ? flowNodeSelectionStore.clearSelection()
            : flowNodeSelectionStore.selectFlowNode({
                flowNodeId: incident.flowNodeId,
                flowNodeInstanceId: incident.flowNodeInstanceId,
                isMultiInstance: false,
              });
        }}
        checkIsRowSelected={(rowId) => {
          const incident = sortedIncidents.find(({id}) => id === rowId);
          if (incident === undefined) {
            return false;
          }
          return incident.isSelected;
        }}
        onSort={(sortKey: string) => {
          tracking.track({
            eventName: 'incidents-sorted',
            column: sortKey,
          });
        }}
        headerColumns={[
          {
            header: 'Incident Type',
            key: 'errorType',
          },
          {
            header: 'Failing Flow Node',
            key: 'flowNodeName',
          },
          {
            header: 'Job Id',
            key: 'jobId',
            isDisabled: !isJobIdPresent,
          },
          {
            header: 'Creation Date',
            key: 'creationTime',
            isDefault: true,
          },
          {
            header: 'Error Message',
            key: 'errorMessage',
            isDisabled: true,
          },
          ...(hasIncidentInCalledInstance
            ? [
                {
                  header: 'Root Cause Instance',
                  key: 'rootCauseInstance',
                },
              ]
            : []),
          ...(hasPermissionForRetryOperation
            ? [
                {
                  header: 'Operations',
                  key: 'operations',
                  isDisabled: true,
                },
              ]
            : []),
        ]}
        rows={sortedIncidents.map((incident) => {
          const {rootCauseInstance} = incident;
          const areOperationsVisible =
            rootCauseInstance === null ||
            rootCauseInstance.instanceId === processInstanceId;

          return {
            id: incident.id,
            errorType: incident.errorType.name,
            flowNodeName: incident.flowNodeName,
            jobId: incident.jobId || '--',
            creationTime: formatDate(incident.creationTime),
            errorMessage: (
              <FlexContainer>
                <ErrorMessageCell>{incident.errorMessage}</ErrorMessageCell>
                {incident.errorMessage.length >= 58 && (
                  <Button
                    size="sm"
                    kind="ghost"
                    onClick={(event) => {
                      event.stopPropagation();

                      tracking.track({
                        eventName: 'incidents-panel-full-error-message-opened',
                      });

                      handleMoreButtonClick(
                        incident.errorMessage,
                        incident.flowNodeName,
                      );
                    }}
                  >
                    More
                  </Button>
                )}
              </FlexContainer>
            ),
            rootCauseInstance:
              rootCauseInstance !== null ? (
                rootCauseInstance.instanceId === processInstanceId ? (
                  '--'
                ) : (
                  <Link
                    to={{
                      pathname: Paths.processInstance(
                        rootCauseInstance.instanceId,
                      ),
                    }}
                    title={`View root cause instance ${rootCauseInstance.processDefinitionName} - ${rootCauseInstance.instanceId}`}
                  >
                    {`${rootCauseInstance.processDefinitionName} - ${rootCauseInstance.instanceId}`}
                  </Link>
                )
              ) : undefined,

            operations:
              hasPermissionForRetryOperation && areOperationsVisible ? (
                <IncidentOperation
                  instanceId={processInstanceId}
                  incident={incident}
                  showSpinner={incident.hasActiveOperation}
                />
              ) : undefined,
          };
        })}
      />
      <JSONEditorModal
        isVisible={isModalVisible}
        title={modalTitle}
        value={modalContent}
        onClose={handleModalClose}
        readOnly
      />
    </>
  );
});

export {IncidentsTable};
