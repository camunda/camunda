/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useMemo, useState} from 'react';
import update from 'immutability-helper';

import {
  Button,
  Input,
  LabeledInput,
  Modal,
  Select,
  DurationChart,
  LoadingIndicator,
  Deleter,
  Tooltip,
  Icon,
  Message,
} from 'components';
import {evaluateReport, formatters, numberParser} from 'services';
import {t} from 'translation';
import {newReport} from 'config';
import {withErrorHandling} from 'HOC';
import {addNotification, showError} from 'notifications';

import {loadTenants} from './service';
import ResultPreview from './ResultPreview';

import './TimeGoalsModal.scss';

const defaultGoals = [
  {
    type: 'targetDuration',
    percentile: '75',
    value: '7',
    unit: 'days',
  },
  {
    type: 'slaDuration',
    percentile: '99',
    value: '7',
    unit: 'days',
  },
];

export function TimeGoalsModal({onClose, onConfirm, mightFail, process}) {
  const isEditing = process.durationGoals?.goals?.length > 0;
  const [data, setData] = useState();
  const [isGoalVisible, setIsGoalVisible] = useState(
    defaultGoals.map((goal) =>
      isEditing ? process.durationGoals?.goals?.some(({type}) => goal.type === type) : true
    )
  );
  const [goals, setGoals] = useState(
    defaultGoals.map(
      (goal) => process.durationGoals?.goals?.find(({type}) => goal.type === type) || goal
    )
  );
  const [deleting, setDeleting] = useState();

  useEffect(() => {
    (async () => {
      const tenantData = await loadTenants(process.processDefinitionKey);
      mightFail(
        evaluateReport(getReportPayload(process.processDefinitionKey, tenantData), []),
        ({result}) => setData(result),
        showError
      );
    })();
  }, [mightFail, process]);

  useEffect(() => {
    if (data?.instanceCount > 0 && !isEditing) {
      const targetDuration = findPercentageDuration(data, 0.8);
      const slaDuration = findPercentageDuration(data, 0.99);

      updateGoalValue(0, 'value', targetDuration.value);
      updateGoalValue(0, 'unit', targetDuration.unit);
      updateGoalValue(1, 'value', slaDuration.value);
      updateGoalValue(1, 'unit', slaDuration.unit);
    }
  }, [data, isEditing]);

  function updateGoalValue(idx, prop, value) {
    setGoals((currentGoals) => update(currentGoals, {[idx]: {[prop]: {$set: value}}}));
  }

  const isDurationValuesValid = goals.every((goal) => numberParser.isPositiveInt(goal.value));
  const visibleGoals = useMemo(
    () => goals.filter((_, idx) => isGoalVisible[idx]),
    [goals, isGoalVisible]
  );

  return (
    <Modal open size="max" onClose={() => !deleting && onClose()} className="TimeGoalsModal">
      <Modal.Header>{t('processes.timeGoals.label')}</Modal.Header>
      <Modal.Content>
        {data ? (
          <>
            <div className="GoalsSection">
              <fieldset className="goalsConfig">
                <legend className="title">
                  {t('processes.timeGoals.configure')}{' '}
                  <Tooltip
                    content={
                      <div>
                        {t('processes.timeGoals.setDuration')}
                        <br />
                        <br />
                        {t('processes.timeGoals.availableGoals')}
                      </div>
                    }
                  >
                    <Icon type="info" />
                  </Tooltip>
                </legend>
                {goals.map(({type, value, percentile, unit}, idx) => (
                  <div className="singleGoal" key={type}>
                    <b>{t('processes.timeGoals.' + type)}</b>
                    <Select
                      value={percentile.toString()}
                      onChange={(selectValue) => updateGoalValue(idx, 'percentile', selectValue)}
                    >
                      <Select.Option value="99">99%</Select.Option>
                      <Select.Option value="95">95%</Select.Option>
                      <Select.Option value="90">90%</Select.Option>
                      <Select.Option value="75">75%</Select.Option>
                      <Select.Option value="25">25%</Select.Option>
                    </Select>
                    <span>
                      {t('processes.timeGoals.instancesTake')}{' '}
                      <b>{t('processes.timeGoals.lessThan')}</b>
                    </span>
                    <Input
                      isInvalid={!numberParser.isPositiveInt(value)}
                      type="text"
                      value={value}
                      onChange={(evt) => updateGoalValue(idx, 'value', evt.target.value)}
                      maxLength="8"
                    />
                    <Select
                      className="unitSelection"
                      value={unit}
                      onChange={(selectValue) => updateGoalValue(idx, 'unit', selectValue)}
                    >
                      <Select.Option value="millis">
                        {t('common.unit.milli.label-plural')}
                      </Select.Option>
                      <Select.Option value="seconds">
                        {t('common.unit.second.label-plural')}
                      </Select.Option>
                      <Select.Option value="minutes">
                        {t('common.unit.minute.label-plural')}
                      </Select.Option>
                      <Select.Option value="hours">
                        {t('common.unit.hour.label-plural')}
                      </Select.Option>
                      <Select.Option value="days">
                        {t('common.unit.day.label-plural')}
                      </Select.Option>
                      <Select.Option value="weeks">
                        {t('common.unit.week.label-plural')}
                      </Select.Option>
                      <Select.Option value="months">
                        {t('common.unit.month.label-plural')}
                      </Select.Option>
                      <Select.Option value="years">
                        {t('common.unit.year.label-plural')}
                      </Select.Option>
                    </Select>
                    <LabeledInput
                      type="checkbox"
                      label={t('processes.timeGoals.displayGoal')}
                      checked={isGoalVisible[idx]}
                      onChange={(evt) =>
                        setIsGoalVisible((visibleGoals) =>
                          update(visibleGoals, {[idx]: {$set: evt.target.checked}})
                        )
                      }
                    />
                  </div>
                ))}
                {!isDurationValuesValid && (
                  <Message className="positiveIntegerError" error>
                    {t('common.errors.positiveInt')}
                  </Message>
                )}
              </fieldset>
              <ResultPreview
                goals={visibleGoals}
                processDefinitionKey={process.processDefinitionKey}
              />
            </div>
            <h3 className="chartTitle title">
              {t('processes.timeGoals.durationDistribution')}{' '}
              <Tooltip
                position="bottom"
                content={t('processes.timeGoals.durationDistributionInfo')}
              >
                <Icon type="info" />
              </Tooltip>
            </h3>
            <DurationChart data={data.measures[0].data} colors="#1991c8" />
          </>
        ) : (
          <LoadingIndicator />
        )}
        <Deleter
          type="goals"
          entity={deleting}
          onClose={() => setDeleting()}
          getName={({processName}) => processName}
          deleteEntity={async () => {
            await onConfirm([]);
            addNotification({
              type: 'success',
              text: t('processes.goalRemoved', {processName: deleting.processName}),
            });
          }}
        />
      </Modal.Content>
      <Modal.Actions>
        {isEditing && (
          <Button link className="deleteButton" onClick={() => setDeleting(process)}>
            {t('common.deleteEntity', {entity: t('processes.goals')})}
          </Button>
        )}
        <Button main onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button
          main
          primary
          onClick={() => {
            onConfirm(visibleGoals);
          }}
          disabled={!data || !isDurationValuesValid}
        >
          {isEditing ? t('processes.timeGoals.updateGoals') : t('processes.timeGoals.updateGoals')}
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
                value: '30',
                unit: 'days',
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

function findPercentageDuration(data, percentage) {
  // find the duration bucket that contains nth percentile instance
  // and return the duration of the next bucket to ensure that goals succeed
  // if the nth percentile instance is in the last bucket, return its duration even if goals fail
  const targetDurationPosition = percentage * data.instanceCount - 1;
  const durationData = data?.measures[0].data;
  let instancesCounter = 0;
  for (let idx = 0; idx < durationData.length; idx++) {
    instancesCounter += durationData[idx].value;
    if (instancesCounter > targetDurationPosition) {
      const durationBucket = durationData[idx + 1] || durationData[idx];
      const {value, unit} = formatters.convertToDecimalTimeUnit(durationBucket.key);
      return {value: Math.ceil(value).toString(), unit};
    }
  }
}
