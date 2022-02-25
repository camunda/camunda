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
import {evaluateReport, formatters} from 'services';
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
      : [
          {
            type: 'targetDuration',
            percentile: '75',
            value: '',
            unit: null,
            visible: true,
          },
          {
            type: 'slaDuration',
            percentile: '99',
            value: '',
            unit: null,
            visible: true,
          },
        ]
  );

  useEffect(() => {
    (async () => {
      const tenantData = await loadTenants(processDefinitionKey);
      mightFail(
        evaluateReport(getReportPayload(processDefinitionKey, tenantData), []),
        ({result}) => setData(result),
        showError
      );
    })();
  }, [mightFail, processDefinitionKey]);

  useEffect(() => {
    if (data?.instanceCount > 0 && (!initialGoals || !initialGoals.length)) {
      const targetDuration = findPercentageDuration(data, 0.8);
      const slaDuration = findPercentageDuration(data, 0.99);

      updateGoalValue(0, 'value', targetDuration.value);
      updateGoalValue(0, 'unit', targetDuration.unit);
      updateGoalValue(1, 'value', slaDuration.value);
      updateGoalValue(1, 'unit', slaDuration.unit);
    }
  }, [data, initialGoals]);

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
              <Select
                value={percentile}
                onChange={(selectValue) => updateGoalValue(idx, 'percentile', selectValue)}
              >
                <Select.Option value="99">99%</Select.Option>
                <Select.Option value="95">95%</Select.Option>
                <Select.Option value="90">90%</Select.Option>
                <Select.Option value="75">75%</Select.Option>
                <Select.Option value="25">25%</Select.Option>
              </Select>
              <span>
                {t('processes.timeGoals.instancesTake')} <b>{t('processes.timeGoals.lessThan')}</b>
              </span>
              <Input
                type="text"
                value={value}
                onChange={(evt) => updateGoalValue(idx, 'value', evt.target.value)}
              />
              <Select
                className="unitSelection"
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
        {data ? (
          <DurationChart data={data?.measures[0].data} colors="#1991c8" />
        ) : (
          <LoadingIndicator />
        )}
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
    },
  });
}

function findPercentageDuration(data, percentage) {
  const targetDurationPosition = percentage * data.instanceCount - 1;
  const durationData = data?.measures[0].data;
  let instancesCounter = 0;
  for (let idx = 0; idx < durationData.length; idx++) {
    instancesCounter += durationData[idx].value;
    if (instancesCounter > targetDurationPosition) {
      const durationBucket = durationData[idx + 1] || durationData[idx];
      const {value, unit} = formatters.convertToBiggestPossibleDuration(durationBucket.key);
      return {value: Math.ceil(value).toString(), unit};
    }
  }
}
