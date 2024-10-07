/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useCallback, useEffect, useState} from 'react';
import {DecisionTree, Settings} from '@carbon/icons-react';
import {Column, Grid} from '@carbon/react';

import {EntityList, EmptyState, PageTitle} from 'components';
import {t} from 'translation';
import {withErrorHandling, withUser} from 'HOC';
import {addNotification, showError} from 'notifications';
import {isUserSearchAvailable, getOptimizeDatabase} from 'config';
import {track} from 'tracking';

import {DashboardView} from '../Dashboards/DashboardView';
import KpiResult from './KpiResult';
import KpiSummary from './KpiSummary';
import ConfigureProcessModal from './ConfigureProcessModal';
import KpiTooltip from './KpiTooltip';
import {loadProcesses, updateProcess, loadManagementDashboard} from './service';

import './Processes.scss';

export function Processes({mightFail, user}) {
  const [processes, setProcesses] = useState();
  const [sorting, setSorting] = useState();
  const [editProcessConfig, setEditProcessConfig] = useState();
  const [userSearchAvailable, setUserSearchAvailable] = useState();
  const [dashboard, setDashboard] = useState();
  const [optimizeDatabase, setOptimizeDatabase] = useState();

  useEffect(() => {
    mightFail(loadManagementDashboard(), setDashboard, showError);
  }, [mightFail]);

  const loadProcessesList = useCallback(
    (sortBy, sortOrder) => {
      setSorting({key: sortBy, order: sortOrder});
      mightFail(loadProcesses(sortBy, sortOrder), setProcesses, showError);
    },
    [mightFail]
  );

  useEffect(() => {
    loadProcessesList();
  }, [loadProcessesList]);

  useEffect(() => {
    (async () => {
      setUserSearchAvailable(await isUserSearchAvailable());
      setOptimizeDatabase(await getOptimizeDatabase());
    })();
  }, []);

  const headers = [
    t('common.name'),
    <>
      {t('processes.timeKpi')} <KpiTooltip />
    </>,
    <>
      {t('processes.qualityKpi')} <KpiTooltip />
    </>,
  ];

  if (userSearchAvailable) {
    const ownerColumn = t('processes.owner');
    headers.splice(1, 0, ownerColumn);
  }

  const processesLabel =
    processes?.length === 1 ? t('processes.label') : t('processes.label-plural');

  return (
    <Grid condensed className="Processes" fullWidth>
      <Column sm={4} md={8} lg={16}>
        <PageTitle pageName={t('processes.defaultDashboardAndKPI')} />
        {optimizeDatabase === 'elasticsearch' && (
          <>
            <h1 className="processOverview">
              {t('processes.adoptionDashboard')}
              {processes && (
                <div className="info">
                  <span>
                    {t('processes.analysing', {count: processes.length, label: processesLabel})}
                  </span>
                </div>
              )}
            </h1>
            {dashboard && (
              <DashboardView
                tiles={dashboard.tiles}
                customizeReportLink={(id) => `/processes/report/${id}/`}
              />
            )}
          </>
        )}
        <EntityList
          title={t('processes.defaultDashboardAndKPI')}
          description={(query, count) =>
            processes && query
              ? t('processes.processesListedOf', {
                  count,
                  total: processes.length,
                  label: processesLabel,
                })
              : t('processes.processesListed', {total: processes.length, label: processesLabel})
          }
          emptyStateComponent={<EmptyState title={t('processes.empty')} icon={<DecisionTree />} />}
          isLoading={!processes}
          headers={headers}
          sorting={sorting}
          onChange={loadProcessesList}
          rows={processes?.map(
            ({processDefinitionKey, processDefinitionName, owner, digest, kpis}) => {
              const kpisWithData = kpis.filter(({value, target}) => value && target);
              const timeKpis = kpisWithData?.filter((kpi) => kpi.type === 'time');
              const qualityKpis = kpisWithData?.filter((kpi) => kpi.type === 'quality');
              const meta = [<KpiSummary kpis={timeKpis} />, <KpiSummary kpis={qualityKpis} />];

              let listItem = {
                id: processDefinitionKey,
                type: t('common.process.label'),
                icon: <DecisionTree />,
                name: processDefinitionName || processDefinitionKey,
                meta,
                actions: [],
              };

              if (userSearchAvailable) {
                meta.unshift(owner?.name);

                listItem.actions.push({
                  icon: <Settings />,
                  text: t('common.configure'),
                  action: () => setEditProcessConfig({processDefinitionKey, owner, digest}),
                });
              }

              if (
                user?.authorizations.includes('entity_editor') &&
                optimizeDatabase !== 'opensearch'
              ) {
                listItem.link = `dashboard/instant/${processDefinitionKey}/`;
              }

              return listItem;
            }
          )}
        />
        {editProcessConfig && (
          <ConfigureProcessModal
            initialConfig={editProcessConfig}
            onClose={() => setEditProcessConfig()}
            onConfirm={(newConfig, emailEnabled, ownerName) => {
              setEditProcessConfig();
              mightFail(
                updateProcess(editProcessConfig.processDefinitionKey, newConfig),
                () => {
                  if (emailEnabled) {
                    if (newConfig.processDigest.enabled) {
                      addNotification({
                        type: 'success',
                        text: t('processes.digestConfigured', {name: ownerName}),
                      });
                    }
                    trackEmailDigestState(
                      newConfig.processDigest.enabled,
                      editProcessConfig.processDefinitionKey
                    );
                  }
                  loadProcessesList();
                },
                showError
              );
            }}
          />
        )}
      </Column>
    </Grid>
  );
}

export default withUser(withErrorHandling(Processes));

function trackEmailDigestState(isEnabled, processDefinitionKey) {
  track('emailDigest' + (isEnabled ? 'Enabled' : 'Disabled'), {
    processDefinitionKey,
  });
}
