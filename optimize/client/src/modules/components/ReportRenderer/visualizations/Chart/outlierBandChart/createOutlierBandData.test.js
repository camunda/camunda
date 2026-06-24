/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import createOutlierBandData from './createOutlierBandData';

function percentileMeasure(value, data) {
  return {property: 'totalTokens', aggregationType: {type: 'percentile', value}, data};
}

function buildProps(measures) {
  return {
    report: {
      result: {measures},
      data: {
        configuration: {color: 'blue'},
        visualization: 'outlierBand',
        groupBy: {type: '', value: ''},
        view: {properties: ['totalTokens'], entity: 'processInstance'},
      },
    },
    theme: 'light',
  };
}

it('should build three bands ordered P5, P50, P95 regardless of the measure order', () => {
  // given measures in a non-ascending order
  const measures = [
    percentileMeasure(95, [
      {key: 'wk1', value: 3000},
      {key: 'wk2', value: 3100},
    ]),
    percentileMeasure(5, [
      {key: 'wk1', value: 500},
      {key: 'wk2', value: 520},
    ]),
    percentileMeasure(50, [
      {key: 'wk1', value: 1300},
      {key: 'wk2', value: 1320},
    ]),
  ];

  // when
  const {labels, datasets} = createOutlierBandData(buildProps(measures));

  // then — ordered low → high, aligned on shared buckets
  expect(datasets).toHaveLength(3);
  expect(datasets.map((dataset) => dataset.label)).toEqual(['P5', 'P50', 'P95']);
  expect(datasets[0].data).toEqual([500, 520]);
  expect(datasets[1].data).toEqual([1300, 1320]);
  expect(datasets[2].data).toEqual([3000, 3100]);
  expect(labels).toEqual(['wk1', 'wk2']);
});

it('should fill the lowest band to the origin and higher bands to the band below', () => {
  // given
  const measures = [
    percentileMeasure(5, [{key: 'wk1', value: 500}]),
    percentileMeasure(50, [{key: 'wk1', value: 1300}]),
    percentileMeasure(95, [{key: 'wk1', value: 3000}]),
  ];

  // when
  const {datasets} = createOutlierBandData(buildProps(measures));

  // then
  expect(datasets[0].fill).toBe('origin');
  expect(datasets[1].fill).toBe('-1');
  expect(datasets[2].fill).toBe('-1');
});

it('should apply a semi-transparent fill derived from the band border colour', () => {
  // given
  const measures = [
    percentileMeasure(5, [{key: 'wk1', value: 500}]),
    percentileMeasure(50, [{key: 'wk1', value: 1300}]),
    percentileMeasure(95, [{key: 'wk1', value: 3000}]),
  ];

  // when
  const {datasets} = createOutlierBandData(buildProps(measures));

  // then — fill is an rgba derived from the (solid) border colour, so the two differ
  datasets.forEach((dataset) => {
    expect(dataset.borderColor).toBeTruthy();
    expect(dataset.backgroundColor).toContain('rgba');
    expect(dataset.backgroundColor).not.toEqual(dataset.borderColor);
    expect(dataset.legendColor).toEqual(dataset.borderColor);
  });
});

it('should return empty data when there are no measures', () => {
  expect(createOutlierBandData(buildProps([]))).toEqual({labels: [], datasets: []});
});
