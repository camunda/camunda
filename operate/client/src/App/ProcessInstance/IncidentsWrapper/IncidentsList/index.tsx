/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {Button, DataTableSkeleton, InlineLoading} from '@carbon/react';
import {RetryFailed} from '@carbon/react/icons';
import {DataTable} from 'modules/components/DataTable';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {ErrorMessage} from 'modules/components/ErrorMessage';
import {formatDate} from 'modules/utils/date';
import {getIncidentErrorName} from 'modules/utils/incidents';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/Routes';
import type {EnhancedIncident} from 'modules/hooks/incidents';
import {copilotStore} from 'modules/stores/copilot';
import {useResolveIncident} from 'modules/mutations/incidents/useResolveIncident';
import {handleOperationError} from 'modules/utils/notifications';
import {tracking} from 'modules/tracking';
import AISparkle from 'modules/components/Icon/ai-sparkle.svg?react';
import {
  ListContainer,
  ExpandedPanel,
  ErrorLabel,
  ErrorText,
  MetaRow,
  MetaLabel,
  MetaValue,
  ActionsRow,
} from './styled';

type Props = {
  incidents: EnhancedIncident[];
  state: 'skeleton' | 'loading' | 'error' | 'empty' | 'content';
  processInstanceKey?: string;
};

const HEADERS = [
  {key: 'errorType', header: 'Incident Type', width: '30%'},
  {key: 'elementName', header: 'Failing Element', width: '30%'},
  {key: 'creationTime', header: 'Created', width: '40%'},
];

const ExpandedDetail: React.FC<{
  incident: EnhancedIncident;
}> = ({incident}) => {
  const {isPending, mutate: resolveIncident} = useResolveIncident({
    incidentKey: incident.incidentKey,
    jobKey: incident.jobKey ?? undefined,
    onError: (error) => {
      handleOperationError(error.status);
    },
    onSuccess: () => {
      tracking.track({
        eventName: 'single-operation',
        operationType: 'RESOLVE_INCIDENT',
        source: 'incident-table',
      });
    },
  });

  return (
    <ExpandedPanel>
      <ErrorLabel>Error message</ErrorLabel>
      <ErrorText>{incident.errorMessage ?? '—'}</ErrorText>

      {incident.jobKey && (
        <MetaRow>
          <MetaLabel>Job ID</MetaLabel>
          <MetaValue>{incident.jobKey}</MetaValue>
        </MetaRow>
      )}

      <ActionsRow>
        <Button
          kind="ghost"
          size="sm"
          renderIcon={() => (
            <AISparkle style={{width: '14px', height: '12px'}} />
          )}
          onClick={() => copilotStore.openWithIncidentExplanation()}
        >
          Explain with Copilot
        </Button>
        {isPending && <InlineLoading data-testid="operation-spinner" />}
        <Button
          kind="ghost"
          size="sm"
          renderIcon={RetryFailed}
          onClick={(e) => {
            e.stopPropagation();
            resolveIncident();
          }}
          disabled={isPending}
          data-testid="retry-incident"
        >
          Retry
        </Button>
      </ActionsRow>
    </ExpandedPanel>
  );
};

const IncidentsList: React.FC<Props> = observer(function IncidentsList({
  state,
  incidents,
  processInstanceKey,
}) {
  if (state === 'skeleton') {
    return (
      <ListContainer>
        <DataTableSkeleton
          data-testid="incidents-list-skeleton"
          columnCount={HEADERS.length}
          rowCount={3}
          showHeader={false}
          showToolbar={false}
          headers={HEADERS}
        />
      </ListContainer>
    );
  }

  if (state === 'empty') {
    return (
      <EmptyMessage message="There are no Incidents matching this filter set" />
    );
  }

  if (state === 'error') {
    return <ErrorMessage />;
  }

  const expandedContents = Object.fromEntries(
    incidents.map((incident) => [
      incident.incidentKey,
      <ExpandedDetail key={incident.incidentKey} incident={incident} />,
    ]),
  );

  return (
    <ListContainer>
      <DataTable
        isExpandable
        expandableRowTitle="Toggle incident details"
        headers={HEADERS}
        rows={incidents.map((incident) => ({
          id: incident.incidentKey,
          isExpanded: incidents.length === 1,
          errorType: getIncidentErrorName(incident.errorType),
          elementName:
            processInstanceKey === incident.processInstanceKey ? (
              incident.elementName
            ) : (
              <Link
                to={{
                  pathname: Paths.processInstance(incident.processInstanceKey),
                  search: `?elementId=${incident.elementId}`,
                }}
                title={`View root cause instance ${incident.processDefinitionName} - ${incident.processInstanceKey}`}
              >
                {`${incident.elementId} - ${incident.processDefinitionName} - ${incident.processInstanceKey}`}
              </Link>
            ),
          creationTime: formatDate(incident.creationTime),
        }))}
        expandedContents={expandedContents}
      />
    </ListContainer>
  );
});

export {IncidentsList};
