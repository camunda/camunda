/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import Table from 'modules/components/Table';
import Button from 'modules/components/Button';
import ColumnHeader from '../../../Instances/ListPanel/List/ColumnHeader';
import {TransitionGroup} from 'modules/components/Transition';
import {IncidentOperation} from 'modules/components/IncidentOperation';

import {formatDate} from 'modules/utils/date';
import {getSorting} from 'modules/utils/filter';
import {sortData, sortIncidents} from './service';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {observer} from 'mobx-react';
import {useInstancePageParams} from 'App/Instance/useInstancePageParams';
import {ErrorMessageModal} from './ErrorMessageModal';

import * as Styled from './styled';
import {Restricted} from 'modules/components/Restricted';
import {IS_NEXT_INCIDENTS} from 'modules/feature-flags';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {Incident, incidentsStore} from 'modules/stores/incidents';
import {Link} from 'modules/components/Link';
import {Locations} from 'modules/routes';

const {THead, TBody, TR, TD} = Table;

type Props = {
  incidents?: unknown[];
  // TODO: remove this prop, when removing IS_NEXT_INCIDENTS
};

const IncidentsTable: React.FC<Props> = observer(function IncidentsTable({
  incidents,
}) {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [modalContent, setModalContent] = useState<string | null>(null);
  const [modalTitle, setModalTitle] = useState<string | null>(null);
  const {processInstanceId} = useInstancePageParams();
  const {sortBy, sortOrder} = getSorting('instance');
  const {filteredIncidents} = incidentsStore;

  const handleModalClose = () => {
    setIsModalVisible(false);
    setModalContent(null);
    setModalTitle(null);
  };

  const handleMoreButtonClick = (
    errorMessage: string,
    flowNodeName: string
  ) => {
    setIsModalVisible(true);
    setModalContent(errorMessage);
    setModalTitle(`Flow Node "${flowNodeName}" Error`);
  };

  const sortedIncidents: Incident[] = IS_NEXT_INCIDENTS
    ? sortIncidents(filteredIncidents, sortBy, sortOrder)
    : sortData(incidents, sortBy, sortOrder);

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
            {IS_NEXT_INCIDENTS &&
              singleInstanceDiagramStore.hasCalledInstances && (
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
              const areOperationsVisible = IS_NEXT_INCIDENTS
                ? rootCauseInstance === null ||
                  rootCauseInstance.instanceId === processInstanceId
                : true;

              //TODO: remove when IS_NEXT_INCIDENTS is removed
              const isSelected = flowNodeSelectionStore.isSelected({
                flowNodeId: incident.flowNodeId,
                flowNodeInstanceId: incident.flowNodeInstanceId,
                isMultiInstance: false,
              });

              return (
                <Styled.Transition
                  key={incident.id}
                  timeout={{enter: 500, exit: 200}}
                  mountOnEnter
                  unmountOnExit
                >
                  <Styled.IncidentTR
                    data-testid={`tr-incident-${incident.id}`}
                    aria-selected={
                      IS_NEXT_INCIDENTS ? incident.isSelected : isSelected
                    }
                    isSelected={
                      IS_NEXT_INCIDENTS ? incident.isSelected : isSelected
                    }
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
                    aria-label={`Incident ${
                      IS_NEXT_INCIDENTS
                        ? incident.errorType.name
                        : incident.errorType
                    }`}
                  >
                    <TD>
                      <Styled.FirstCell>
                        <Styled.Index>{index + 1}</Styled.Index>
                        {IS_NEXT_INCIDENTS
                          ? incident.errorType.name
                          : incident.errorType}
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
                    {IS_NEXT_INCIDENTS &&
                      singleInstanceDiagramStore.hasCalledInstances &&
                      rootCauseInstance !== null && (
                        <TD>
                          {rootCauseInstance.instanceId ===
                          processInstanceId ? (
                            '--'
                          ) : (
                            <Link
                              to={(location) =>
                                Locations.instance(
                                  rootCauseInstance.instanceId,
                                  location
                                )
                              }
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
