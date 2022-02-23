/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useCallback, useEffect, useState} from 'react';

import {Button, EntityList} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import TimeGoalsModal from './TimeGoalsModal';
import {loadProcesses, updateGoals} from './service';

import './Processes.scss';

export function Processes({mightFail}) {
  const [processes, setProcesses] = useState();
  const [sorting, setSorting] = useState();
  const [openProcess, setOpenProcess] = useState();

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
        columns={[
          {name: t('common.name'), key: 'processName', defaultOrder: 'asc'},
          t('processes.owner'),
          t('processes.timeGoal'),
        ]}
        sorting={sorting}
        onChange={loadProcessesList}
        data={processes?.map(({processDefinitionKey, processName, owner, timeGoals}) => ({
          id: processDefinitionKey,
          type: t('common.process.label'),
          icon: 'data-source',
          name: processName || processDefinitionKey,
          meta: [
            owner,
            <Button
              className="setGoalBtn"
              onClick={() => {
                setOpenProcess({processDefinitionKey, processName, timeGoals});
              }}
            >
              {timeGoals?.length > 0 ? t('processes.editGoal') : t('processes.setGoal')}
            </Button>,
          ],
        }))}
      />
      {openProcess && (
        <TimeGoalsModal
          process={openProcess}
          onClose={() => setOpenProcess()}
          onConfirm={(goals) => {
            mightFail(
              updateGoals(openProcess.processDefinitionKey, goals),
              setOpenProcess,
              showError
            );
          }}
          onRemove={loadProcessesList}
        />
      )}
    </div>
  );
}

export default withErrorHandling(Processes);
