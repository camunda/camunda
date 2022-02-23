/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState} from 'react';
import update from 'immutability-helper';

import {
  Button,
  Input,
  LabeledInput,
  Modal,
  Select,
  DurationChart,
  LoadingIndicator,
} from 'components';
import {evaluateReport} from 'services';
import {t} from 'translation';
import {newReport} from 'config';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import {loadTenants} from './service';

import './TimeGoalsModal.scss';

export function TimeGoalsModal({
  onClose,
  onConfirm,
  mightFail,
  processDefinitionKey,
  initialGoals,
}) {
  const [data, setData] = useState();
  const [goals, setGoals] = useState(
    initialGoals?.length > 0
      ? initialGoals
      : ['targetDuration', 'slaDuration'].map((type) => ({
          type,
          percentile: '',
          value: '',
          unit: null,
          visible: true,
        }))
  );

  useEffect(() => {
    (async () => {
      const tenantData = await loadTenants(processDefinitionKey);
      mightFail(
        evaluateReport(getReportPayload(processDefinitionKey, tenantData), []),
        ({result}) => setData(result.measures[0].data),
        showError
      );
    })();
  }, [mightFail, processDefinitionKey]);

  function updateGoalValue(idx, prop, value) {
    setGoals((currentGoals) => update(currentGoals, {[idx]: {[prop]: {$set: value}}}));
  }

  return (
    <Modal open size="max" onClose={onClose} className="TimeGoalsModal">
      <Modal.Header>{t('processes.timeGoals.label')}</Modal.Header>
      <Modal.Content>
        <fieldset className="goalsConfig">
          <legend>{t('processes.timeGoals.configure')}</legend>
          {goals.map(({type, value, percentile, unit, visible}, idx) => (
            <div className="singleGoal" key={type}>
              <b>{t('processes.timeGoals.' + type)}</b>
              <div className="percentageInput">
                <Input
                  type="text"
                  value={percentile}
                  onChange={(evt) => updateGoalValue(idx, 'percentile', evt.target.value)}
                />
                <span>%</span>
              </div>
              <span>
                {t('processes.timeGoals.instancesTake')} <b>{t('processes.timeGoals.lessThan')}</b>
              </span>
              <Input
                type="text"
                value={value}
                onChange={(evt) => updateGoalValue(idx, 'value', evt.target.value)}
              />
              <Select
                value={unit}
                onChange={(selectValue) => updateGoalValue(idx, 'unit', selectValue)}
              >
                <Select.Option value="millis">{t('common.unit.milli.label-plural')}</Select.Option>
                <Select.Option value="seconds">
                  {t('common.unit.second.label-plural')}
                </Select.Option>
                <Select.Option value="minutes">
                  {t('common.unit.minute.label-plural')}
                </Select.Option>
                <Select.Option value="hours">{t('common.unit.hour.label-plural')}</Select.Option>
                <Select.Option value="days">{t('common.unit.day.label-plural')}</Select.Option>
                <Select.Option value="weeks">{t('common.unit.week.label-plural')}</Select.Option>
                <Select.Option value="months">{t('common.unit.month.label-plural')}</Select.Option>
                <Select.Option value="years">{t('common.unit.year.label-plural')}</Select.Option>
              </Select>
              <LabeledInput
                type="checkbox"
                label={t('processes.timeGoals.displayGoal')}
                checked={visible}
                onChange={(evt) => updateGoalValue(idx, 'visible', evt.target.checked)}
              />
            </div>
          ))}
        </fieldset>
        <h3 className="chartTitle">{t('processes.timeGoals.durationDistribution')}</h3>
        {data ? <DurationChart data={data} colors="#1991c8" /> : <LoadingIndicator />}
      </Modal.Content>
      <Modal.Actions>
        <Button main onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button main primary onClick={() => onConfirm(goals)}>
          {initialGoals?.length > 0
            ? t('processes.timeGoals.updateGoals')
            : t('processes.timeGoals.saveGoals')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}

export default withErrorHandling(TimeGoalsModal);

function getReportPayload(processDefinitionKey, tenantData) {
  return update(newReport.new, {
    data: {
      definitions: {
        $set: [
          {
            key: processDefinitionKey,
            versions: ['all'],
            tenantIds: tenantData[0].tenants.map(({id}) => id),
          },
        ],
      },
      view: {
        $set: {
          entity: 'processInstance',
          properties: ['frequency'],
        },
      },
      groupBy: {$set: {type: 'duration', value: null}},
      visualization: {$set: 'bar'},
      filter: {
        $set: [
          {
            type: 'instanceEndDate',
            data: {
              type: 'rolling',
              start: {
                value: '1',
                unit: 'months',
              },
              end: null,
            },
            appliedTo: ['all'],
            filterLevel: 'instance',
          },
        ],
      },
    },
  });
}
