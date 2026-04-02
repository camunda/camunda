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
import {
  ExpandedContent,
  ExpandedField,
  FieldLabel,
  ErrorMessageCell,
  FlexContainer,
  ChildIncidentContainer,
} from './styled';
import {Link} from 'modules/components/Link';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {Paths} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {Button, Stack} from '@carbon/react';
import {SortableTable} from 'modules/components/SortableTable';
import {useState, useMemo} from 'react';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {
  getIncidentErrorName,
  isSingleIncidentSelected,
} from 'modules/utils/incidents';
import type {EnhancedIncident} from 'modules/hooks/incidents';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';

type DecisionInstanceLookup = Record<
  string,
  {decisionInstanceKey: string; decisionDefinitionName: string}
>;

const getElementName = (
  incident: EnhancedIncident,
  processInstanceKey: string,
  decisionInstancesByElementKey?: DecisionInstanceLookup,
): React.ReactNode => {
  const decisionInstance =
    decisionInstancesByElementKey &&
    (incident.errorType === 'DECISION_EVALUATION_ERROR' ||
      incident.errorType === 'CALLED_DECISION_ERROR') &&
    decisionInstancesByElementKey[incident.elementInstanceKey];

  if (decisionInstance) {
    return (
      <Link
        to={Paths.decisionInstance(decisionInstance.decisionInstanceKey)}
        title={`View root cause decision ${decisionInstance.decisionDefinitionName} - ${decisionInstance.decisionInstanceKey}`}
        onClick={(e) => e.stopPropagation()}
      >
        {`${decisionInstance.decisionDefinitionName} - ${decisionInstance.decisionInstanceKey}`}
      </Link>
    );
  }

  if (processInstanceKey !== incident.processInstanceKey) {
    return (
      <Link
        to={{
          pathname: Paths.processInstance(incident.processInstanceKey),
          search: `?elementId=${incident.elementId}`,
        }}
        title={`View root cause instance ${incident.processDefinitionName} - ${incident.processInstanceKey}`}
        onClick={(e) => e.stopPropagation()}
      >
        {`${incident.elementId} - ${incident.processDefinitionName} - ${incident.processInstanceKey}`}
      </Link>
    );
  }

  return incident.elementName;
};

type ChildInstanceWithIncident = {
  type: 'process' | 'decision';
  key: string;
  name: string;
};

type IncidentsTableProps = {
  processInstanceKey: string;
  incidents: EnhancedIncident[];
  decisionInstancesByElementKey?: DecisionInstanceLookup;
  childInstanceWithIncident?: ChildInstanceWithIncident;
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
    decisionInstancesByElementKey,
    childInstanceWithIncident,
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

    const expandedContent = useMemo(
      () =>
        Object.fromEntries(
          incidents.map((incident) => [
            incident.incidentKey,
            <ExpandedContent key={incident.incidentKey}>
              <ExpandedField>
                <FieldLabel>Job ID</FieldLabel>
                <span>{incident.jobKey || '—'}</span>
              </ExpandedField>
              <ExpandedField>
                <FieldLabel>Error message</FieldLabel>
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
              </ExpandedField>
            </ExpandedContent>,
          ]),
        ),
      [incidents],
    );

    if (state === 'empty' && childInstanceWithIncident) {
      const {type, key, name} = childInstanceWithIncident;
      const linkTo =
        type === 'process'
          ? Paths.processInstance(key)
          : Paths.decisionInstance(key);

      return (
        <ChildIncidentContainer>
          <Stack gap={5}>
            <EmptyMessage
              message="No incidents found on this element."
              additionalInfo="The incident may originate from a called instance."
            />
            <Link to={linkTo} title={`View in ${name}`}>
              {`View in ${name}`}
            </Link>
          </Stack>
        </ChildIncidentContainer>
      );
    }

    return (
      <>
        <SortableTable
          state={state}
          isExpandable
          emptyMessage={{
            message: 'There are no incidents matching this filter set',
          }}
          selectionType="row"
          columnsWithNoContentPadding={['operations']}
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
              header: 'Type',
              key: 'errorType',
            },
            {
              header: 'Failing Element',
              key: 'elementName',
              isDisabled: true,
            },
            {
              header: 'Created',
              key: 'creationTime',
              isDefault: true,
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
              elementName: getElementName(
                incident,
                processInstanceKey,
                decisionInstancesByElementKey,
              ),
              creationTime: formatDate(incident.creationTime),
              operations: areOperationsVisible ? (
                <IncidentOperation
                  incidentKey={incident.incidentKey}
                  jobKey={incident.jobKey}
                />
              ) : undefined,
            };
          })}
          expandedContent={expandedContent}
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
