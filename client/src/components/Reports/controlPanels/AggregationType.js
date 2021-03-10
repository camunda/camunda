/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Popover, Icon, Form, Switch} from 'components';
import {t} from 'translation';

import './AggregationType.scss';

const aggregationOrder = ['sum', 'min', 'avg', 'median', 'max'];

export default function AggregationType({report, onChange}) {
  const {aggregationTypes} = report.configuration;

  const isDurationReport = report?.view?.properties.includes('duration');
  const isVariableReport = report?.view?.entity === 'variable';

  function isLastAggregation(type) {
    return aggregationTypes.length === 1 && aggregationTypes[0] === type;
  }

  function hasAggregation(type) {
    return aggregationTypes.includes(type);
  }

  function addAggregation(type) {
    const newAggregations = [...aggregationTypes, type].sort(
      (a, b) => aggregationOrder.indexOf(a) - aggregationOrder.indexOf(b)
    );

    return onChange(
      {
        configuration: {
          aggregationTypes: {
            $set: newAggregations,
          },
          aggregationType: {$set: newAggregations[0]},
          targetValue: {active: {$set: false}},
        },
        distributedBy: {$set: {type: 'none', value: null}},
      },
      true
    );
  }

  function removeAggregation(type) {
    const remainingAggregations = aggregationTypes.filter((existingType) => existingType !== type);
    return onChange(
      {
        configuration: {
          aggregationTypes: {
            $set: remainingAggregations,
          },
          aggregationType: {$set: remainingAggregations[0]},
        },
      },
      true
    );
  }

  if (isDurationReport || isVariableReport) {
    const availableAggregations = [];
    if (isVariableReport) {
      availableAggregations.push('sum');
    }
    availableAggregations.push('min', 'avg');
    if (!report.configuration.processPart) {
      availableAggregations.push('median');
    }
    availableAggregations.push('max');

    let popoverTitle = t('report.config.aggregationShort.' + aggregationTypes[0]);
    if (availableAggregations.every(hasAggregation)) {
      popoverTitle = 'All';
    } else if (aggregationTypes.length > 1) {
      popoverTitle = 'Multi';
    }

    return (
      <Popover
        className="AggregationType"
        title={
          <>
            <span className="content">{popoverTitle}</span>
            <Icon className="editIcon" type="edit" />
          </>
        }
      >
        <h4>
          {t(
            'report.config.aggregation.' + (isVariableReport ? 'variableLegend' : 'durationLegend')
          )}
        </h4>
        <Form compact>
          <fieldset>
            {availableAggregations.map((type) => (
              <Switch
                key={type}
                label={t('report.config.aggregation.' + type)}
                checked={hasAggregation(type)}
                disabled={isLastAggregation(type)}
                onChange={({target}) => {
                  if (target.checked) {
                    addAggregation(type);
                  } else {
                    removeAggregation(type);
                  }
                }}
              />
            ))}
          </fieldset>
        </Form>
      </Popover>
    );
  }
  return null;
}
