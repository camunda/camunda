/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import Table from 'modules/components/Table';
import {Button} from 'modules/components/Button';
import {ColumnHeader} from './ColumnHeader';
import {TransitionGroup} from 'modules/components/Transition';
import {IncidentOperation} from 'modules/components/IncidentOperation';
import {formatDate} from 'modules/utils/date';
import {getSortParams} from 'modules/utils/filter';
import {sortIncidents} from './service';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {observer} from 'mobx-react';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {ErrorMessageModal} from './ErrorMessageModal';
import * as Styled from './styled';
import {Restricted} from 'modules/components/Restricted';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {Incident, incidentsStore} from 'modules/stores/incidents';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/routes';

const {THead, TBody, TR, TD} = Table;

const IncidentsTable: React.FC = observer(function IncidentsTable() {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [modalContent, setModalContent] = useState<string>('');
  const [modalTitle, setModalTitle] = useState<string>('');
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const {sortBy, sortOrder} = getSortParams() || {
    sortBy: 'errorType',
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

  return (
    <>
      <Table data-testid="incidents-table">
        <THead>
          <TR>
            <Styled.FirstTH>
              <Styled.Fake />
              <ColumnHeader
                sortKey="errorType"
                label="Incident Type"
                table="instance"
              />
            </Styled.FirstTH>
            <Styled.TH>
              <ColumnHeader
                sortKey="flowNodeName"
                label="Flow Node"
                table="instance"
              />
            </Styled.TH>
            <Styled.TH>
              <ColumnHeader
                sortKey="jobId"
                label="Job Id"
                disabled={!isJobIdPresent}
                table="instance"
              />
            </Styled.TH>
            <Styled.TH>
              <ColumnHeader
                sortKey="creationTime"
                label="Creation Time"
                table="instance"
              />
            </Styled.TH>
            <Styled.TH>
              <ColumnHeader label="Error Message" />
            </Styled.TH>
            {processInstanceDetailsDiagramStore.hasCalledProcessInstances && (
              <Styled.TH>
                <ColumnHeader label="Root Cause Instance" />
              </Styled.TH>
            )}
            <Restricted scopes={['write']}>
              <Styled.TH>
                <ColumnHeader label="Operations" />
              </Styled.TH>
            </Restricted>
          </TR>
        </THead>

        <TBody>
          <TransitionGroup component={null}>
            {sortedIncidents.map((incident, index: number) => {
              const {rootCauseInstance} = incident;
              const areOperationsVisible =
                rootCauseInstance === null ||
                rootCauseInstance.instanceId === processInstanceId;

              return (
                <Styled.Transition
                  key={incident.id}
                  timeout={{enter: 500, exit: 200}}
                  mountOnEnter
                  unmountOnExit
                >
                  <Styled.IncidentTR
                    data-testid={`tr-incident-${incident.id}`}
                    aria-selected={incident.isSelected}
                    isSelected={incident.isSelected}
                    onClick={() => {
                      incidentsStore.isSingleIncidentSelected(
                        incident.flowNodeInstanceId
                      )
                        ? flowNodeSelectionStore.clearSelection()
                        : flowNodeSelectionStore.selectFlowNode({
                            flowNodeId: incident.flowNodeId,
                            flowNodeInstanceId: incident.flowNodeInstanceId,
                            isMultiInstance: false,
                          });
                    }}
                    aria-label={`Incident ${incident.errorType.name}`}
                  >
                    <TD>
                      <Styled.FirstCell>
                        <Styled.Index>{index + 1}</Styled.Index>
                        {incident.errorType.name}
                      </Styled.FirstCell>
                    </TD>
                    <TD>
                      <div>{incident.flowNodeName}</div>
                    </TD>
                    <TD>
                      <div>{incident.jobId || '--'}</div>
                    </TD>
                    <TD>
                      <div>{formatDate(incident.creationTime)}</div>
                    </TD>
                    <TD>
                      <Styled.Flex>
                        <Styled.ErrorMessageCell>
                          {incident.errorMessage}
                        </Styled.ErrorMessageCell>
                        {incident.errorMessage.length >= 58 && (
                          <Button
                            size="small"
                            onClick={(event) => {
                              event.stopPropagation();
                              handleMoreButtonClick(
                                incident.errorMessage,
                                incident.flowNodeName
                              );
                            }}
                          >
                            More...
                          </Button>
                        )}
                      </Styled.Flex>
                    </TD>
                    {processInstanceDetailsDiagramStore.hasCalledProcessInstances &&
                      rootCauseInstance !== null && (
                        <TD>
                          {rootCauseInstance.instanceId ===
                          processInstanceId ? (
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
                          )}
                        </TD>
                      )}
                    <Restricted scopes={['write']}>
                      <TD>
                        {areOperationsVisible && (
                          <IncidentOperation
                            instanceId={processInstanceId}
                            incident={incident}
                            showSpinner={incident.hasActiveOperation}
                          />
                        )}
                      </TD>
                    </Restricted>
                  </Styled.IncidentTR>
                </Styled.Transition>
              );
            })}
          </TransitionGroup>
        </TBody>
      </Table>
      <ErrorMessageModal
        isVisible={isModalVisible}
        title={modalTitle}
        content={modalContent}
        onModalClose={handleModalClose}
      />
    </>
  );
});

export {IncidentsTable};
