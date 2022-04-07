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

import TimeGoalsModal from './TimeGoalsModal';
import GoalResult from './GoalResult';
import GoalSummary from './GoalSummary';
import EditOwnerModal from './EditOwnerModal';
import {loadProcesses, updateGoals, updateOwner} from './service';

import './Processes.scss';

export function Processes({mightFail}) {
  const [processes, setProcesses] = useState();
  const [sorting, setSorting] = useState();
  const [openProcess, setOpenProcess] = useState();
  const [editOwnerInfo, setEditOwnerInfo] = useState();
  const [optimizeProfile, setOptimizeProfile] = useState();

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

  const columns = [
    {name: t('common.name'), key: 'processName', defaultOrder: 'asc'},
    {name: t('processes.timeGoal'), key: 'durationGoals', defaultOrder: 'asc'},
  ];

  if (optimizeProfile === 'cloud' || optimizeProfile === 'platform') {
    const ownerColumn = {name: t('processes.owner'), key: 'owner', defaultOrder: 'asc'};
    columns.splice(1, 0, ownerColumn);
  }

  return (
    <div className="Processes">
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
        data={processes?.map(({processDefinitionKey, processName, owner, durationGoals}) => {
          const meta = [
            <>
              <Tooltip
                position="bottom"
                content={<GoalResult durationGoals={durationGoals} />}
                delay={300}
              >
                <div className="summaryContainer">
                  <GoalSummary goals={durationGoals.results} />
                </div>
              </Tooltip>
              <Button
                className="setGoalBtn"
                onClick={() => {
                  setOpenProcess({processDefinitionKey, processName, durationGoals});
                }}
              >
                {durationGoals?.goals?.length > 0
                  ? t('processes.editGoal')
                  : t('processes.setGoal')}
              </Button>
            </>,
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
            name: processName || processDefinitionKey,
            meta,
          };
        })}
      />
      {openProcess && (
        <TimeGoalsModal
          process={openProcess}
          onClose={() => setOpenProcess()}
          onConfirm={async (goals) => {
            await mightFail(
              updateGoals(openProcess.processDefinitionKey, goals),
              () => {
                setOpenProcess();
                loadProcessesList();
              },
              showError
            );
          }}
        />
      )}
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
