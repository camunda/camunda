/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Edit} from '@carbon/icons-react';
import {Form, FormGroup, Stack, Toggle} from '@carbon/react';

import {Popover} from 'components';
import {t} from 'translation';

import './AggregationType.scss';

const orders = {
  aggregationTypes: ['sum', 'min', 'avg', 'max', 'percentile'],
  userTaskDurationTimes: ['total', 'work', 'idle'],
  percentileAggregations: [99, 95, 90, 75, 50, 25],
};

export default function AggregationType({report, onChange}) {
  const {configuration, distributedBy} = report;
  const {aggregationTypes, processPart} = report.configuration;

  const isDurationReport = report?.view?.properties.includes('duration');
  const isUserTaskReport = report?.view?.entity === 'userTask';
  const isVariableReport = report?.view?.entity === 'variable';
  const isIncidentReport = report?.view?.entity === 'incident';

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
      if (a.type === 'percentile' && b.type === 'percentile') {
        return b.value - a.value;
      }

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
        isTabTip
        trigger={
          <Popover.Button
            size="sm"
            kind="ghost"
            renderIcon={Edit}
            iconDescription={t('report.config.aggregation.label')}
          >
            {popoverTitle}
          </Popover.Button>
        }
        floating
      >
        <Form>
          <Stack gap={3}>
            {isUserTaskReport && (
              <FormGroup legendText={t('report.config.aggregation.userTaskLegend')}>
                <Stack gap={3}>
                  {orders.userTaskDurationTimes.map((type) => (
                    <Toggle
                      key={type}
                      id={type}
                      size="sm"
                      labelText={t('report.config.userTaskDuration.' + type)}
                      hideLabel
                      toggled={hasAggregation('userTaskDurationTimes', type)}
                      disabled={isLastAggregation('userTaskDurationTimes', type)}
                      onToggle={(checked) => {
                        if (checked) {
                          addAggregation('userTaskDurationTimes', type);
                        } else {
                          removeAggregation('userTaskDurationTimes', type);
                        }
                      }}
                    />
                  ))}
                </Stack>
              </FormGroup>
            )}
            <FormGroup
              legendText={t(
                'report.config.aggregation.' +
                  (isVariableReport ? 'variableLegend' : 'durationLegend')
              )}
            >
              <Stack gap={3}>
                {availableAggregations.map((type) => (
                  <Toggle
                    key={type}
                    id={type}
                    size="sm"
                    labelText={t('report.config.aggregation.' + type)}
                    hideLabel
                    toggled={hasAggregation('aggregationTypes', type)}
                    disabled={isLastAggregation('aggregationTypes', type)}
                    onToggle={(checked) => {
                      if (checked) {
                        addAggregation('aggregationTypes', type);
                      } else {
                        removeAggregation('aggregationTypes', type);
                      }
                    }}
                  />
                ))}
              </Stack>
            </FormGroup>
            {distributedBy.type !== 'process' && !processPart && (
              <FormGroup legendText={t('report.config.aggregation.percentileLegend')}>
                <Stack gap={3}>
                  {orders.percentileAggregations.map((value) => {
                    const defaultLabel = `P${value}`;
                    return (
                      <Toggle
                        key={defaultLabel}
                        id={defaultLabel}
                        size="sm"
                        labelText={value === 50 ? t('report.config.aggregation.p50') : defaultLabel}
                        hideLabel
                        toggled={hasAggregation('aggregationTypes', 'percentile', value)}
                        disabled={isLastAggregation('aggregationTypes', 'percentile', value)}
                        onToggle={(checked) => {
                          if (checked) {
                            addAggregation('aggregationTypes', 'percentile', value);
                          } else {
                            removeAggregation('aggregationTypes', 'percentile', value);
                          }
                        }}
                      />
                    );
                  })}
                </Stack>
              </FormGroup>
            )}
          </Stack>
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

  return agg.value === value;
}
