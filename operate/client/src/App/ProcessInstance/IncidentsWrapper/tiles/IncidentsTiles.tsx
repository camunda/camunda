/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useState} from 'react';
import {Button, Tile} from '@carbon/react';
import styled from 'styled-components';
import {formatDate} from 'modules/utils/date';
import {getFilteredIncidents} from 'modules/utils/incidents';
import {useIncidents} from 'modules/hooks/incidents';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';
import {IncidentOperation} from 'modules/components/IncidentOperation';

const Grid = styled.div`
  padding: var(--cds-spacing-05);
  display: grid;
  grid-template-columns: 1fr;
  grid-auto-rows: minmax(min-content, max-content);
  gap: var(--cds-spacing-04);
  overflow: auto;
`;

const TitleRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--cds-spacing-03);
`;

const Title = styled.span`
  font-weight: 600;
  color: var(--cds-text-primary);
  font-size: 14px;
  line-height: 18px;
`;

const ErrorMessage = styled.p<{expanded: boolean}>`
  color: var(--cds-text-primary);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: ${({expanded}) => (expanded ? 'unset' : 3)};
  -webkit-box-orient: vertical;
  font-size: 14px;
  line-height: 18px;
`;

const MetaRow = styled.div`
  display: grid;
  grid-template-columns: 1fr;
  row-gap: var(--cds-spacing-02);
  color: var(--cds-text-secondary);
  margin: var(--cds-spacing-04) 0 0 0;
`;

const Label = styled.span`
  color: var(--cds-text-secondary);
`;

const Value = styled.span`
  color: var(--cds-text-primary);
`;

const Row = styled.div`
  display: flex;
  gap: var(--cds-spacing-02);
`;

const IncidentsTiles: React.FC = observer(function IncidentsTiles() {
  const incidents = useIncidents();
  const filteredIncidents = getFilteredIncidents(incidents);
  const hasPermissionForRetryOperation =
    processInstanceDetailsStore.hasPermission(['UPDATE_PROCESS_INSTANCE']);
  const [expandedById, setExpandedById] = useState<Record<string, boolean>>({});
  const {processInstanceId = ''} = useProcessInstancePageParams();

  if (filteredIncidents.length === 0) {
    return (
      <Grid>
        <span>No incidents</span>
      </Grid>
    );
  }

  return (
    <Grid>
      {filteredIncidents.map((incident) => {
        const isExpanded = expandedById[incident.id] ?? false;
        const shouldShowErrorMessage = incident.errorMessage && 
          incident.errorMessage !== incident.errorType.name;
        const showToggle = shouldShowErrorMessage && incident.errorMessage.length > 120;

        return (
          <Tile key={incident.id} data-testid={`incident-tile-${incident.id}`}>
            <TitleRow>
              <Title>{incident.errorType.name}</Title>
              {hasPermissionForRetryOperation && (
                <IncidentOperation
                  instanceId={processInstanceId}
                  incident={incident}
                  showSpinner={incident.hasActiveOperation}
                />
              )}
            </TitleRow>

            {shouldShowErrorMessage && (
              <>
                <ErrorMessage expanded={isExpanded}>
                  {incident.errorMessage}
                </ErrorMessage>
                {showToggle && (
                  <Button
                    size="sm"
                    kind="ghost"
                    onClick={() =>
                      setExpandedById((map) => ({
                        ...map,
                        [incident.id]: !isExpanded,
                      }))
                    }
                  >
                    {isExpanded ? 'Hide' : 'Show more'}
                  </Button>
                )}
              </>
            )}

            <MetaRow>
              <Row>
                <Label>Failing Flow Node:</Label>
                <Value>{incident.flowNodeName}</Value>
              </Row>
              {incident.jobId !== null && (
                <Row>
                  <Label>Job Id:</Label>
                  <Value>{incident.jobId}</Value>
                </Row>
              )}
              <Row>
                <Label>Creation Date:</Label>
                <Value>{formatDate(incident.creationTime)}</Value>
              </Row>
            </MetaRow>
          </Tile>
        );
      })}
    </Grid>
  );
});

export {IncidentsTiles};
