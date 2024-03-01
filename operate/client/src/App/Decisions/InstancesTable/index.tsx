/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {PanelHeader} from 'modules/components/PanelHeader';
import {SortableTable} from 'modules/components/SortableTable';
import {StateIcon} from 'modules/components/StateIcon';
import {decisionInstancesStore} from 'modules/stores/decisionInstances';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {formatDate} from 'modules/utils/date';
import {useEffect} from 'react';
import {useLocation} from 'react-router-dom';
import {Container, DecisionName} from './styled';
import {observer} from 'mobx-react';
import {Paths} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {Link} from 'modules/components/Link';
import {useFilters} from 'modules/hooks/useFilters';
import {getDecisionInstanceFilters} from 'modules/utils/filter';

const ROW_HEIGHT = 34;

const InstancesTable: React.FC = observer(() => {
  const {
    state: {
      status,
      filteredDecisionInstancesCount,
      latestFetch,
      decisionInstances,
    },
    areDecisionInstancesEmpty,
    hasLatestDecisionInstances,
  } = decisionInstancesStore;

  const location = useLocation();
  const filters = useFilters();

  const {isInitialLoadComplete} = groupedDecisionsStore;

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const decisionId = params.get('name');
    const tenantId = params.get('tenant');

    if (isInitialLoadComplete && !location.state?.refreshContent) {
      if (
        decisionId !== null &&
        !groupedDecisionsStore.isSelectedDecisionValid({
          decisionId,
          tenantId,
        })
      ) {
        return;
      }

      decisionInstancesStore.fetchDecisionInstancesFromFilters();
    }
  }, [location.search, isInitialLoadComplete, location.state]);

  useEffect(() => {
    if (isInitialLoadComplete && location.state?.refreshContent) {
      decisionInstancesStore.fetchDecisionInstancesFromFilters();
    }
  }, [isInitialLoadComplete, location.state]);

  const getTableState = () => {
    if (['initial', 'first-fetch'].includes(status)) {
      return 'skeleton';
    }
    if (status === 'fetching') {
      return 'loading';
    }
    if (status === 'error') {
      return 'error';
    }
    if (areDecisionInstancesEmpty) {
      return 'empty';
    }

    return 'content';
  };

  const getEmptyListMessage = () => {
    return {
      message: 'There are no Instances matching this filter set',
      additionalInfo: filters.areDecisionInstanceStatesApplied()
        ? undefined
        : 'To see some results, select at least one Instance state',
    };
  };

  const {tenant} = getDecisionInstanceFilters(location.search);

  const isTenantColumnVisible =
    window.clientConfig?.multiTenancyEnabled &&
    (tenant === undefined || tenant === 'all');

  return (
    <Container>
      <PanelHeader
        title="Decision Instances"
        count={filteredDecisionInstancesCount}
      />
      <SortableTable
        state={getTableState()}
        emptyMessage={getEmptyListMessage()}
        onVerticalScrollStartReach={async (scrollDown) => {
          if (decisionInstancesStore.shouldFetchPreviousInstances() === false) {
            return;
          }

          await decisionInstancesStore.fetchPreviousInstances();

          if (hasLatestDecisionInstances) {
            scrollDown(latestFetch?.decisionInstancesCount ?? 0 * ROW_HEIGHT);
          }
        }}
        onVerticalScrollEndReach={() => {
          if (decisionInstancesStore.shouldFetchNextInstances() === false) {
            return;
          }

          decisionInstancesStore.fetchNextInstances();
        }}
        rows={decisionInstances.map(
          ({
            id,
            state,
            decisionName,
            decisionVersion,
            tenantId,
            evaluationDate,
            processInstanceId,
          }) => {
            return {
              id,
              decisionName: (
                <DecisionName>
                  <StateIcon
                    state={state}
                    data-testid={`${state}-icon-${id}`}
                    size={20}
                  />
                  {decisionName}
                </DecisionName>
              ),
              decisionInstanceKey: (
                <Link
                  to={Paths.decisionInstance(id)}
                  onClick={() => {
                    tracking.track({
                      eventName: 'navigation',
                      link: 'decision-instances-parent-process-details',
                    });
                  }}
                  title={`View decision instance ${id}`}
                  aria-label={`View decision instance ${id}`}
                >
                  {id}
                </Link>
              ),
              decisionVersion,
              tenant: isTenantColumnVisible ? tenantId : undefined,
              evaluationDate: formatDate(evaluationDate),
              processInstanceId: (
                <>
                  {processInstanceId !== null ? (
                    <Link
                      to={Paths.processInstance(processInstanceId)}
                      title={`View process instance ${processInstanceId}`}
                      aria-label={`View process instance ${processInstanceId}`}
                      onClick={() => {
                        tracking.track({
                          eventName: 'navigation',
                          link: 'decision-instances-parent-process-details',
                        });
                      }}
                    >
                      {processInstanceId}
                    </Link>
                  ) : (
                    'None'
                  )}
                </>
              ),
            };
          },
        )}
        headerColumns={[
          {
            header: 'Name',
            key: 'decisionName',
          },
          {
            header: 'Decision Instance Key',
            key: 'decisionInstanceKey',
            sortKey: 'id',
          },
          {
            header: 'Version',
            key: 'decisionVersion',
          },
          ...(isTenantColumnVisible
            ? [
                {
                  header: 'Tenant',
                  key: 'tenant',
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
            key: 'processInstanceId',
          },
        ]}
      />
    </Container>
  );
});

export {InstancesTable};
