/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Select} from 'components';

import './AggregationType.scss';

export default function AggregationType({
  report: {
    result: {type},
    data
  },
  onChange
}) {
  if (type && type.toLowerCase().includes('duration')) {
    return (
      <fieldset className="AggregationType">
        <legend>Aggregation</legend>
        <Select
          value={data.configuration.aggregationType}
          onChange={evt => onChange({aggregationType: {$set: evt.target.value}})}
        >
          <Select.Option value="min">Minimum</Select.Option>
          <Select.Option value="avg">Average</Select.Option>
          <Select.Option value="median">Median</Select.Option>
          <Select.Option value="max">Maximum</Select.Option>
        </Select>
      </fieldset>
    );
  }
  return null;
}
