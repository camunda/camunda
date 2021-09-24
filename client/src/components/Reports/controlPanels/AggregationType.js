/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';

import {Popover, Icon, Form, Switch, Tooltip} from 'components';
import {t} from 'translation';
import {isOptimizeCloudEnvironment} from 'config';

import './AggregationType.scss';

const orders = {
  aggregationTypes: ['sum', 'min', 'avg', 'median', 'max'],
  userTaskDurationTimes: ['total', 'work', 'idle'],
};

export default function AggregationType({report, onChange}) {
  const {configuration, distributedBy} = report;
  const {aggregationTypes} = report.configuration;

  const isDurationReport = report?.view?.properties.includes('duration');
  const isUserTaskReport = report?.view?.entity === 'userTask';
  const isVariableReport = report?.view?.entity === 'variable';
  const isIncidentReport = report?.view?.entity === 'incident';
  const [isOptimizeCloud, setIsOptimizeCloud] = useState(true);

  useEffect(() => {
    (async () => {
      setIsOptimizeCloud(await isOptimizeCloudEnvironment());
    })();
  }, []);

  function isLastAggregation(field, type) {
    return configuration[field].length === 1 && configuration[field][0] === type;
  }

  function hasAggregation(field, type) {
    return configuration[field].includes(type);
  }

  function addAggregation(field, type) {
    const newAggregations = [...configuration[field], type].sort(
      (a, b) => orders[field].indexOf(a) - orders[field].indexOf(b)
    );

    const changes = {
      configuration: {
        [field]: {
          $set: newAggregations,
        },
        targetValue: {active: {$set: false}},
      },
    };

    return onChange(changes, true);
  }

  function removeAggregation(field, type) {
    const remainingAggregations = configuration[field].filter(
      (existingType) => existingType !== type
    );

    return onChange(
      {
        configuration: {
          [field]: {
            $set: remainingAggregations,
          },
        },
      },
      true
    );
  }

  if (isDurationReport || isVariableReport) {
    const availableAggregations = [];
    if (!isIncidentReport) {
      availableAggregations.push('sum');
    }
    availableAggregations.push('min', 'avg');
    if (!report.configuration.processPart) {
      availableAggregations.push('median');
    }
    availableAggregations.push('max');

    let popoverTitle = t('report.config.aggregationShort.' + aggregationTypes[0]);
    if (
      availableAggregations.every((aggregation) => hasAggregation('aggregationTypes', aggregation))
    ) {
      popoverTitle = 'All';
    } else if (aggregationTypes.length > 1) {
      popoverTitle = 'Multi';
    }

    return (
      <Popover
        className="AggregationType"
        renderInPortal="AggregationType"
        title={
          <>
            <span className="content">{popoverTitle}</span>
            <Icon className="editIcon" type="edit-small" />
          </>
        }
      >
        <Form compact>
          {isUserTaskReport && !isOptimizeCloud && (
            <>
              <h4>{t('report.config.aggregation.userTaskLegend')}</h4>
              <fieldset>
                {orders.userTaskDurationTimes.map((type) => (
                  <div key={type}>
                    <span>
                      <Switch
                        label={t('report.config.userTaskDuration.' + type)}
                        checked={hasAggregation('userTaskDurationTimes', type)}
                        disabled={isLastAggregation('userTaskDurationTimes', type)}
                        onChange={({target}) => {
                          if (target.checked) {
                            addAggregation('userTaskDurationTimes', type);
                          } else {
                            removeAggregation('userTaskDurationTimes', type);
                          }
                        }}
                      />
                    </span>
                  </div>
                ))}
              </fieldset>
            </>
          )}
          <h4>
            {t(
              'report.config.aggregation.' +
                (isVariableReport ? 'variableLegend' : 'durationLegend')
            )}
          </h4>
          <fieldset>
            {availableAggregations.map((type) => (
              <div key={type}>
                <Tooltip
                  content={
                    type === 'median' && distributedBy.type === 'process'
                      ? t('report.config.aggregation.multiProcessWarning')
                      : undefined
                  }
                >
                  <span>
                    <Switch
                      label={t('report.config.aggregation.' + type)}
                      checked={hasAggregation('aggregationTypes', type)}
                      disabled={
                        isLastAggregation('aggregationTypes', type) ||
                        (type === 'median' && distributedBy.type === 'process')
                      }
                      onChange={({target}) => {
                        if (target.checked) {
                          addAggregation('aggregationTypes', type);
                        } else {
                          removeAggregation('aggregationTypes', type);
                        }
                      }}
                    />
                  </span>
                </Tooltip>
              </div>
            ))}
          </fieldset>
        </Form>
      </Popover>
    );
  }
  return null;
}
