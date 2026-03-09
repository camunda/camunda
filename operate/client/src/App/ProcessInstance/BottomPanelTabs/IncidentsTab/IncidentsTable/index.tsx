/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IncidentOperation} from 'modules/components/IncidentOperation';
import {formatDate} from 'modules/utils/date';
import {observer} from 'mobx-react';
import {FlexContainer, ErrorMessageCell} from './styled';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {Button} from '@carbon/react';
import {SortableTable} from 'modules/components/SortableTable';
import {useState} from 'react';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {
  getIncidentErrorName,
  isSingleIncidentSelected,
} from 'modules/utils/incidents';
import type {EnhancedIncident} from 'modules/hooks/incidents';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';

type IncidentsTableProps = {
  processInstanceKey: string;
  incidents: EnhancedIncident[];
  state: React.ComponentProps<typeof SortableTable>['state'];
  onVerticalScrollStartReach?: React.ComponentProps<
    typeof SortableTable
  >['onVerticalScrollStartReach'];
  onVerticalScrollEndReach?: React.ComponentProps<
    typeof SortableTable
  >['onVerticalScrollEndReach'];
};

const IncidentsTable: React.FC<IncidentsTableProps> = observer(
  function IncidentsTable({
    state,
    incidents,
    processInstanceKey,
    onVerticalScrollEndReach,
    onVerticalScrollStartReach,
  }) {
    const [isModalVisible, setIsModalVisible] = useState(false);
    const [modalContent, setModalContent] = useState<string>('');
    const [modalTitle, setModalTitle] = useState<string>('');

    const handleModalClose = () => {
      setIsModalVisible(false);
      setModalContent('');
      setModalTitle('');
    };

    const handleMoreButtonClick = (
      errorMessage: string,
      elementName: string,
    ) => {
      setIsModalVisible(true);
      setModalContent(errorMessage);
      setModalTitle(`Element "${elementName}" Error`);
    };

    const {selectElementInstance, clearSelection} =
      useProcessInstanceElementSelection();

    const isJobKeyPresent = incidents.some(({jobKey}) => !!jobKey);

    return (
      <>
        <SortableTable
          state={state}
          emptyMessage={{
            message: 'There are no Instances matching this filter set',
          }}
          selectionType="row"
          columnsWithNoContentPadding={['operations', 'errorMessage']}
          onSelect={(rowId) => {
            const incident = incidents.find(
              ({incidentKey}) => incidentKey === rowId,
            );
            if (incident === undefined) {
              return;
            }

            if (
              isSingleIncidentSelected(incidents, incident.elementInstanceKey)
            ) {
              clearSelection();
            } else {
              selectElementInstance({
                elementId: incident.elementId,
                elementInstanceKey: incident.elementInstanceKey,
              });
            }
          }}
          checkIsRowSelected={(rowId) => {
            const incident = incidents.find(
              ({incidentKey}) => incidentKey === rowId,
            );
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
          onVerticalScrollStartReach={onVerticalScrollStartReach}
          onVerticalScrollEndReach={onVerticalScrollEndReach}
          headerColumns={[
            {
              header: 'Incident Type',
              key: 'errorType',
            },
            {
              header: 'Failing Element',
              key: 'elementName',
              isDisabled: true,
            },
            {
              header: 'Job Id',
              key: 'jobKey',
              isDisabled: !isJobKeyPresent,
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
            {
              header: 'Operations',
              key: 'operations',
              isDisabled: true,
            },
          ]}
          rows={incidents.map((incident) => {
            const areOperationsVisible =
              processInstanceKey === incident.processInstanceKey;

            return {
              id: incident.incidentKey,
              errorType: getIncidentErrorName(incident.errorType),
              elementName:
                processInstanceKey === incident.processInstanceKey ? (
                  incident.elementName
                ) : (
                  <Link
                    to={{
                      pathname: Paths.processInstance(
                        incident.processInstanceKey,
                      ),
                      search: `?elementId=${incident.elementId}`,
                    }}
                    title={`View root cause instance ${incident.processDefinitionName} - ${incident.processInstanceKey}`}
                  >
                    {`${incident.elementId} - ${incident.processDefinitionName} - ${incident.processInstanceKey}`}
                  </Link>
                ),
              jobKey: incident.jobKey || '--',
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
                          eventName:
                            'incidents-panel-full-error-message-opened',
                        });

                        handleMoreButtonClick(
                          incident.errorMessage,
                          incident.elementName,
                        );
                      }}
                    >
                      More
                    </Button>
                  )}
                </FlexContainer>
              ),
              operations: areOperationsVisible ? (
                <IncidentOperation
                  incidentKey={incident.incidentKey}
                  jobKey={incident.jobKey}
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
  },
);

export {IncidentsTable};
