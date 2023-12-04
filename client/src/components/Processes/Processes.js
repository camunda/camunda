/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useCallback, useEffect, useState} from 'react';

import {EntityList, PageTitle, Tooltip} from 'components';
import {t} from 'translation';
import {withErrorHandling, withUser} from 'HOC';
import {addNotification, showError} from 'notifications';
import {isUserSearchAvailable} from 'config';
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
    })();
  }, []);

  const columns = [
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
    columns.splice(1, 0, ownerColumn);
  }

  const processesLabel =
    processes?.length === 1 ? t('processes.label') : t('processes.label-plural');

  return (
    <div className="Processes">
      <PageTitle pageName={t('processes.defaultDashboardAndKPI')} />
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
      <EntityList
        name={t('processes.defaultDashboardAndKPI')}
        displaySearchInfo={
          processes &&
          ((query, count) => (
            <div className="info">
              {query
                ? t('processes.processesListedOf', {
                    count,
                    total: processes.length,
                    label: processesLabel,
                  })
                : t('processes.processesListed', {total: processes.length, label: processesLabel})}
            </div>
          ))
        }
        empty={t('processes.empty')}
        isLoading={!processes}
        columns={columns}
        sorting={sorting}
        onChange={loadProcessesList}
        forceActionsDropdown
        data={processes?.map(
          ({processDefinitionKey, processDefinitionName, owner, digest, kpis}) => {
            const kpisWithData = kpis.filter(({value, target}) => value && target);
            const timeKpis = kpisWithData?.filter((kpi) => kpi.type === 'time');
            const qualityKpis = kpisWithData?.filter((kpi) => kpi.type === 'quality');
            const meta = [
              <Tooltip position="bottom" content={<KpiResult kpis={timeKpis} />} delay={300}>
                <div className="summaryContainer">
                  <KpiSummary kpis={timeKpis} />
                </div>
              </Tooltip>,
              <Tooltip position="bottom" content={<KpiResult kpis={qualityKpis} />} delay={300}>
                <div className="summaryContainer">
                  <KpiSummary kpis={qualityKpis} />
                </div>
              </Tooltip>,
            ];

            let listItem = {
              id: processDefinitionKey,
              type: t('common.process.label'),
              icon: 'dashboard-optimize',
              name: processDefinitionName || processDefinitionKey,
              meta,
              actions: [],
            };

            if (userSearchAvailable) {
              meta.unshift(owner?.name);

              listItem.actions.push({
                text: t('common.configure'),
                action: () => setEditProcessConfig({processDefinitionKey, owner, digest}),
              });
            }

            if (user?.authorizations.includes('entity_editor')) {
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
    </div>
  );
}

export default withUser(withErrorHandling(Processes));

function trackEmailDigestState(isEnabled, processDefinitionKey) {
  track('emailDigest' + (isEnabled ? 'Enabled' : 'Disabled'), {
    processDefinitionKey,
  });
}
