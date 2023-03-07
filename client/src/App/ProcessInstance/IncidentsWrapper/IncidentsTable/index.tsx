/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {Button} from 'modules/components/Button';
import {IncidentOperation} from 'modules/components/IncidentOperation';
import {formatDate} from 'modules/utils/date';
import {getSortParams} from 'modules/utils/filter';
import {sortIncidents} from './service';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {observer} from 'mobx-react';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {FlexContainer, ErrorMessageCell} from './styled';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {Incident, incidentsStore} from 'modules/stores/incidents';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/routes';
import {useLocation} from 'react-router-dom';
import {tracking} from 'modules/tracking';
import {authenticationStore} from 'modules/stores/authentication';
import {SortableTable} from 'modules/components/SortableTable';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {useResourceBasedPermissions} from 'modules/hooks/useResourceBasedPermissions';

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
    flowNodeName: string
  ) => {
    setIsModalVisible(true);
    setModalContent(errorMessage);
    setModalTitle(`Flow Node "${flowNodeName}" Error`);
  };

  const sortedIncidents: Incident[] = sortIncidents(
    filteredIncidents,
    sortBy,
    sortOrder
  );

  const isJobIdPresent = sortedIncidents.some(({jobId}) => jobId !== null);
  const {hasResourceBasedPermission} = useResourceBasedPermissions();
  const hasPermissionForRetryOperation =
    authenticationStore.hasPermission(['write']) &&
    hasResourceBasedPermission(['UPDATE_PROCESS_INSTANCE']);

  return (
    <>
      <SortableTable
        state="content"
        selectionType="row"
        isScrollable={false}
        onSort={(sortKey: string) => {
          tracking.track({
            eventName: 'incidents-sorted',
            column: sortKey,
          });
        }}
        headerColumns={[
          {
            content: 'Incident Type',
            sortKey: 'errorType',
          },
          {
            content: 'Failing Flow Node',
            sortKey: 'flowNodeName',
          },
          {
            content: 'Job Id',
            sortKey: 'jobId',
            isDisabled: !isJobIdPresent,
          },
          {
            content: 'Creation Date',
            sortKey: 'creationTime',
            isDefault: true,
          },
          {
            content: 'Error Message',
            isDisabled: true,
          },
          ...(processInstanceDetailsDiagramStore.hasCalledProcessInstances
            ? [
                {
                  content: 'Root Cause Instance',
                },
              ]
            : []),
          ...(hasPermissionForRetryOperation
            ? [
                {
                  content: 'Operations',
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
            ariaLabel: `Incident ${incident.errorType.name}`,
            onSelect: () => {
              incidentsStore.isSingleIncidentSelected(
                incident.flowNodeInstanceId
              )
                ? flowNodeSelectionStore.clearSelection()
                : flowNodeSelectionStore.selectFlowNode({
                    flowNodeId: incident.flowNodeId,
                    flowNodeInstanceId: incident.flowNodeInstanceId,
                    isMultiInstance: false,
                  });
            },
            content: [
              {
                cellContent: incident.errorType.name,
              },
              {
                cellContent: incident.flowNodeName,
              },
              {
                cellContent: incident.jobId || '--',
              },
              {
                cellContent: formatDate(incident.creationTime),
              },
              {
                cellContent: (
                  <FlexContainer>
                    <ErrorMessageCell>{incident.errorMessage}</ErrorMessageCell>
                    {incident.errorMessage.length >= 58 && (
                      <Button
                        size="small"
                        type="button"
                        onClick={(event) => {
                          event.stopPropagation();

                          tracking.track({
                            eventName:
                              'incidents-panel-full-error-message-opened',
                          });

                          handleMoreButtonClick(
                            incident.errorMessage,
                            incident.flowNodeName
                          );
                        }}
                      >
                        More...
                      </Button>
                    )}
                  </FlexContainer>
                ),
              },
              ...(processInstanceDetailsDiagramStore.hasCalledProcessInstances &&
              rootCauseInstance !== null
                ? [
                    {
                      cellContent:
                        rootCauseInstance.instanceId === processInstanceId ? (
                          '--'
                        ) : (
                          <Link
                            to={{
                              pathname: Paths.processInstance(
                                rootCauseInstance.instanceId
                              ),
                            }}
                            title={`View root cause instance ${rootCauseInstance.processDefinitionName} - ${rootCauseInstance.instanceId}`}
                          >
                            {`${rootCauseInstance.processDefinitionName} - ${rootCauseInstance.instanceId}`}
                          </Link>
                        ),
                    },
                  ]
                : []),
              ...(hasPermissionForRetryOperation && areOperationsVisible
                ? [
                    {
                      cellContent: (
                        <IncidentOperation
                          instanceId={processInstanceId}
                          incident={incident}
                          showSpinner={incident.hasActiveOperation}
                        />
                      ),
                    },
                  ]
                : []),
            ],
            checkIsSelected: () => {
              return incident.isSelected;
            },
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
