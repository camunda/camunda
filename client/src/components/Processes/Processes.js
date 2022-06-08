/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useCallback, useEffect, useState} from 'react';

import {Button, EntityList, Tooltip} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {getOptimizeProfile} from 'config';

import {DashboardView} from '../Dashboards/DashboardView';
import KpiResult from './KpiResult';
import KpiSummary from './KpiSummary';
import EditOwnerModal from './EditOwnerModal';
import {loadProcesses, updateOwner, loadManagementDashboard} from './service';

import './Processes.scss';

export function Processes({mightFail}) {
  const [processes, setProcesses] = useState();
  const [sorting, setSorting] = useState();
  const [editOwnerInfo, setEditOwnerInfo] = useState();
  const [optimizeProfile, setOptimizeProfile] = useState();
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
      setOptimizeProfile(await getOptimizeProfile());
    })();
  }, []);

  const columns = [t('common.name'), t('processes.timeKpi'), t('processes.qualityKpi')];

  if (optimizeProfile === 'cloud' || optimizeProfile === 'platform') {
    const ownerColumn = t('processes.owner');
    columns.splice(1, 0, ownerColumn);
  }

  return (
    <div className="Processes">
      <h1 className="processOverview">Process Overview</h1>
      {dashboard && (
        <DashboardView
          reports={dashboard.reports}
          availableFilters={dashboard.availableFilters}
          disableNameLink
        />
      )}
      <EntityList
        name={t('processes.title')}
        headerText={
          <div className="goalInfo">
            {t('processes.displayData')}{' '}
            <span className="highlighted">{t('processes.endedThisMonth')}</span>
          </div>
        }
        empty={t('processes.empty')}
        isLoading={!processes}
        columns={columns}
        sorting={sorting}
        onChange={loadProcessesList}
        data={processes?.map(({processDefinitionKey, processDefinitionName, owner, kpis}) => {
          const timeKpis = kpis?.filter((kpi) => kpi.type === 'time');
          const qualityKpis = kpis?.filter((kpi) => kpi.type === 'quality');
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

          if (optimizeProfile === 'cloud' || optimizeProfile === 'platform') {
            meta.unshift(
              <div className="ownerInfo">
                <Tooltip content={owner?.name} overflowOnly>
                  <div className="ownerName">{owner?.name}</div>
                </Tooltip>
                <Button
                  className="setOwnerBtn"
                  onClick={() => setEditOwnerInfo({processDefinitionKey, owner})}
                >
                  {owner?.name ? t('processes.editOwner') : t('processes.addOwner')}
                </Button>
              </div>
            );
          }

          return {
            id: processDefinitionKey,
            type: t('common.process.label'),
            icon: 'data-source',
            name: processDefinitionName || processDefinitionKey,
            meta,
          };
        })}
      />
      {editOwnerInfo && (
        <EditOwnerModal
          initialOwner={editOwnerInfo.owner}
          onClose={() => setEditOwnerInfo()}
          onConfirm={async (userId) => {
            await mightFail(
              updateOwner(editOwnerInfo.processDefinitionKey, userId),
              () => {
                setEditOwnerInfo();
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

export default withErrorHandling(Processes);
