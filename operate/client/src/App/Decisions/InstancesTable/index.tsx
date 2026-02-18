/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {PanelHeader} from 'modules/components/PanelHeader';
import {PaginatedSortableTable} from 'modules/components/PaginatedSortableTable';
import {StateIcon} from 'modules/components/StateIcon';
import {formatDate} from 'modules/utils/date';
import {Container, DecisionName} from './styled';
import {observer} from 'mobx-react';
import {Paths} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {Link} from 'modules/components/Link';
import {useDecisionInstancesSearchPaginated} from 'modules/queries/decisionInstances/useDecisionInstancesSearchPaginated';
import {
  useDecisionInstancesSearchFilter,
  useDecisionInstancesSearchSort,
} from 'modules/hooks/decisionInstancesSearch';
import {getClientConfig} from 'modules/utils/getClientConfig';

const InstancesTable: React.FC = observer(() => {
  const filter = useDecisionInstancesSearchFilter();
  const sort = useDecisionInstancesSearchSort();

  const {
    data,
    status,
    isFetching,
    isFetchingPreviousPage,
    hasPreviousPage,
    fetchPreviousPage,
    isFetchingNextPage,
    hasNextPage,
    fetchNextPage,
  } = useDecisionInstancesSearchPaginated({
    payload: {filter, sort},
    enabled: filter !== undefined,
    select: (data) => {
      tracking.track({
        eventName: 'decisions-loaded',
        filters: Object.keys(filter ?? {}),
        sort,
      });
      return {
        decisionInstances: data.pages.flatMap((page) => page.items),
        totalCount: data.pages.at(0)?.page.totalItems ?? 0,
      };
    },
  });
  const decisionInstances = data?.decisionInstances ?? [];
  const filteredDecisionInstancesCount = data?.totalCount ?? 0;
  const clientConfig = getClientConfig();

  const getTableState = () => {
    switch (true) {
      case filter === undefined:
        return 'empty';
      case status === 'pending' && !data:
        return 'skeleton';
      case isFetching && !isFetchingPreviousPage && !isFetchingNextPage:
        return 'loading';
      case status === 'error':
        return 'error';
      case status === 'success' && filteredDecisionInstancesCount === 0:
        return 'empty';
      default:
        return 'content';
    }
  };

  const getEmptyListMessage = () => {
    return {
      message: 'There are no Instances matching this filter set',
      additionalInfo:
        filter === undefined
          ? 'To see some results, select at least one Instance state'
          : undefined,
    };
  };

  const isTenantColumnVisible =
    clientConfig.multiTenancyEnabled &&
    (filter?.tenantId === undefined || filter?.tenantId === 'all');

  return (
    <Container>
      <PanelHeader
        title="Decision Instances"
        count={filteredDecisionInstancesCount}
      />
      <PaginatedSortableTable
        state={getTableState()}
        emptyMessage={getEmptyListMessage()}
        rows={decisionInstances.map(
          ({
            decisionEvaluationInstanceKey,
            state,
            decisionDefinitionName,
            decisionDefinitionVersion,
            tenantId,
            evaluationDate,
            processInstanceKey,
          }) => {
            return {
              id: decisionEvaluationInstanceKey,
              decisionDefinitionName: (
                <DecisionName>
                  <StateIcon
                    state={state}
                    data-testid={`${state}-icon-${decisionEvaluationInstanceKey}`}
                    size={20}
                  />
                  {decisionDefinitionName}
                </DecisionName>
              ),
              decisionEvaluationInstanceKey: (
                <Link
                  to={Paths.decisionInstance(decisionEvaluationInstanceKey)}
                  onClick={() => {
                    tracking.track({
                      eventName: 'navigation',
                      link: 'decision-instances-parent-process-details',
                    });
                  }}
                  title={`View decision instance ${decisionEvaluationInstanceKey}`}
                  aria-label={`View decision instance ${decisionEvaluationInstanceKey}`}
                >
                  {decisionEvaluationInstanceKey}
                </Link>
              ),
              decisionDefinitionVersion,
              tenantId: isTenantColumnVisible ? tenantId : undefined,
              evaluationDate: formatDate(evaluationDate),
              processInstanceKey: processInstanceKey ? (
                <Link
                  to={Paths.processInstance(processInstanceKey)}
                  title={`View process instance ${processInstanceKey}`}
                  aria-label={`View process instance ${processInstanceKey}`}
                  onClick={() => {
                    tracking.track({
                      eventName: 'navigation',
                      link: 'decision-instances-parent-process-details',
                    });
                  }}
                >
                  {processInstanceKey}
                </Link>
              ) : (
                'None'
              ),
            };
          },
        )}
        headerColumns={[
          {
            header: 'Name',
            key: 'decisionDefinitionName',
          },
          {
            header: 'Decision Instance Key',
            key: 'decisionEvaluationInstanceKey',
          },
          {
            header: 'Version',
            key: 'decisionDefinitionVersion',
          },
          ...(isTenantColumnVisible
            ? [
                {
                  header: 'Tenant',
                  key: 'tenantId',
                },
              ]
            : []),
          {
            header: 'Evaluation Date',
            key: 'evaluationDate',
            isDefault: true,
          },
          {
            header: 'Process Instance Key',
            key: 'processInstanceKey',
          },
        ]}
        pagination={{
          hasPreviousPage,
          hasNextPage,
          isFetchingPreviousPage,
          isFetchingNextPage,
          fetchPreviousPage,
          fetchNextPage,
        }}
      />
    </Container>
  );
});

export {InstancesTable};
