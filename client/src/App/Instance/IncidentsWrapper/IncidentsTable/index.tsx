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
import {sortData} from './service';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {observer} from 'mobx-react';
import {useInstancePageParams} from 'App/Instance/useInstancePageParams';
import {ErrorMessageModal} from './ErrorMessageModal';

import * as Styled from './styled';
const {THead, TBody, TH, TR, TD} = Table;

type Props = {
  incidents: unknown[];
};

const IncidentsTable: React.FC<Props> = observer(function IncidentsTable({
  incidents,
}) {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [modalContent, setModalContent] = useState<string | null>(null);
  const [modalTitle, setModalTitle] = useState<string | null>(null);
  const {processInstanceId} = useInstancePageParams();
  const {sortBy, sortOrder} = getSorting('instance');

  const handleModalClose = () => {
    setIsModalVisible(false);
    setModalContent(null);
    setModalTitle(null);
  };

  const handleMoreButtonClick = (incident: any, e: any) => {
    e.stopPropagation();

    setIsModalVisible(true);
    setModalContent(incident.errorMessage);
    setModalTitle(`Flow Node "${incident.flowNodeName}" Error`);
  };

  const sortedIncidents = sortData(incidents, sortBy, sortOrder);
  const isJobIdPresent = (sortedIncidents: any) =>
    !Boolean(sortedIncidents.find((item: any) => Boolean(item.jobId)));

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
            <TH>
              <ColumnHeader
                sortKey="flowNodeName"
                label="Flow Node"
                table="instance"
              />
            </TH>
            <TH>
              <ColumnHeader
                sortKey="jobId"
                label="Job Id"
                disabled={isJobIdPresent(sortedIncidents)}
                table="instance"
              />
            </TH>
            <TH>
              <ColumnHeader
                sortKey="creationTime"
                label="Creation Time"
                table="instance"
              />
            </TH>
            <TH>
              <ColumnHeader label="Error Message" />
            </TH>
            <TH>
              <ColumnHeader label="Operations" />
            </TH>
          </TR>
        </THead>

        <TBody>
          <TransitionGroup component={null}>
            {sortedIncidents.map((incident: any, index: any) => {
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
                    aria-selected={isSelected}
                    isSelected={isSelected}
                    onClick={() =>
                      flowNodeSelectionStore.selectFlowNode({
                        flowNodeId: incident.flowNodeId,
                        flowNodeInstanceId: incident.flowNodeInstanceId,
                        isMultiInstance: false,
                      })
                    }
                  >
                    <TD>
                      <Styled.FirstCell>
                        <Styled.Index>{index + 1}</Styled.Index>
                        {incident.errorType}
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
                            // @ts-expect-error ts-migrate(2683) FIXME: 'this' implicitly has type 'any' because it does n... Remove this comment to see the full error message
                            onClick={handleMoreButtonClick.bind(this, incident)}
                          >
                            More...
                          </Button>
                        )}
                      </Styled.Flex>
                    </TD>
                    <TD>
                      <IncidentOperation
                        instanceId={processInstanceId}
                        incident={incident}
                        showSpinner={incident.hasActiveOperation}
                      />
                    </TD>
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
