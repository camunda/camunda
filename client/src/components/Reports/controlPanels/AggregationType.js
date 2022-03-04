/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';

import {Popover, Icon, Form, Switch, Tooltip} from 'components';
import {t} from 'translation';
import {getOptimizeProfile} from 'config';

import './AggregationType.scss';

const orders = {
  aggregationTypes: ['sum', 'min', 'avg', 'median', 'max', 'percentile'],
  userTaskDurationTimes: ['total', 'work', 'idle'],
  percentileAggregations: ['99', '95', '90', '75', '50', '25'],
};

export default function AggregationType({report, onChange}) {
  const {configuration, distributedBy} = report;
  const {aggregationTypes} = report.configuration;

  const isDurationReport = report?.view?.properties.includes('duration');
  const isUserTaskReport = report?.view?.entity === 'userTask';
  const isVariableReport = report?.view?.entity === 'variable';
  const isIncidentReport = report?.view?.entity === 'incident';
  const [optimizeProfile, setOptimizeProfile] = useState();

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
    })();
  }, []);

  function isLastAggregation(field, type, value) {
    return (
      configuration[field].length === 1 &&
      getType(field, configuration[field][0]) === type &&
      sameValue(configuration[field][0], value)
    );
  }

  function hasAggregation(field, type, value) {
    return configuration[field].some(
      (agg) => getType(field, agg) === type && sameValue(agg, value)
    );
  }

  function addAggregation(field, type, value) {
    const newAggregations = [
      ...configuration[field],
      constructNewAggregation(field, type, value),
    ].sort((a, b) => {
      return orders[field].indexOf(getType(field, a)) - orders[field].indexOf(getType(field, b));
    });

    return updateReport(field, newAggregations, true);
  }

  function removeAggregation(field, type, value) {
    const remainingAggregations = configuration[field].filter(
      (existingAgg) =>
        getType(field, existingAgg) !== type ||
        (getType(field, existingAgg) === type && !sameValue(existingAgg, value))
    );

    return updateReport(field, remainingAggregations);
  }

  function updateReport(field, aggregations, resetTargetValue) {
    const changes = {
      configuration: {
        [field]: {
          $set: aggregations,
        },
      },
    };

    if (resetTargetValue) {
      changes.configuration.targetValue = {active: {$set: false}};
    }

    return onChange(changes, true);
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

    const {type, value} = aggregationTypes[0];
    let popoverTitle = t('report.config.aggregationShort.' + type, {value});

    if (
      availableAggregations.every((aggregation) =>
        hasAggregation('aggregationTypes', aggregation.type)
      )
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
          {isUserTaskReport && optimizeProfile === 'platform' && (
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
          {distributedBy.type !== 'process' && (
            <>
              <h4>{t('report.config.aggregation.percentileLegend')}</h4>
              <fieldset>
                {orders.percentileAggregations.map((value) => (
                  <div key={value}>
                    <span>
                      <Switch
                        label={value === '50' ? t('report.config.aggregation.p50') : 'P' + value}
                        checked={hasAggregation('aggregationTypes', 'percentile', value)}
                        disabled={isLastAggregation('aggregationTypes', 'percentile', value)}
                        onChange={({target}) => {
                          if (target.checked) {
                            addAggregation('aggregationTypes', 'percentile', value);
                          } else {
                            removeAggregation('aggregationTypes', 'percentile', value);
                          }
                        }}
                      />
                    </span>
                  </div>
                ))}
              </fieldset>
            </>
          )}
        </Form>
      </Popover>
    );
  }
  return null;
}

function getType(field, aggregation) {
  if (field === 'userTaskDurationTimes') {
    return aggregation;
  }

  return aggregation.type;
}

function constructNewAggregation(field, type, value = null) {
  if (field === 'userTaskDurationTimes') {
    return type;
  }

  return {type, value};
}

function sameValue(agg, value) {
  if (!value) {
    return true;
  }

  return agg.value === Number(value);
}
