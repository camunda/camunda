/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ColorPicker} from 'components';
import {t} from 'translation';

import {extractDefaultChartData} from '../defaultChart/createDefaultChartData';
import {addAlpha} from '../colorsUtils';

const BAND_FILL_OPACITY = 0.35;

// Builds one line dataset per percentile measure, ordered low → high (p5 → p50 → p95) so Chart.js
// fill-between renders stacked bands: the lowest band fills down to the axis origin and every
// higher band fills down to the band directly below it. Colours come from Optimize's standard
// series palette (ColorPicker), consistent with every other multi-series chart.
export default function createOutlierBandData(props) {
  const {
    report: {result},
  } = props;
  const measures = result?.measures ?? [];

  if (!measures.length) {
    return {labels: [], datasets: []};
  }

  // Order measure indices by percentile ascending, independent of the response order.
  const orderedIdx = measures
    .map((_measure, idx) => idx)
    .sort((a, b) => percentileOf(measures[a]) - percentileOf(measures[b]));

  const colors = ColorPicker.getGeneratedColors(orderedIdx.length);
  const {labels, labelsMap} = extractDefaultChartData(props, orderedIdx[0]);

  const datasets = orderedIdx.map((measureIdx, bandIdx) => {
    const {formattedResult} = extractDefaultChartData(props, measureIdx);
    const color = colors[bandIdx];

    return {
      yAxisID: 'axis-0',
      label: bandLabel(measures[measureIdx]),
      // align every band on the reference (lowest-percentile) date buckets
      data: alignToBuckets(labelsMap, formattedResult),
      borderColor: color,
      backgroundColor: addAlpha(color, BAND_FILL_OPACITY),
      legendColor: color,
      pointBackgroundColor: color,
      borderWidth: 1,
      fill: bandIdx === 0 ? 'origin' : '-1',
      tension: 0,
    };
  });

  return {labels, datasets};
}

// Looks up each band's value by the reference bucket key so all bands share one x-axis. A band
// missing a bucket yields null (rendered as a gap) rather than throwing.
function alignToBuckets(refLabelsMap, formattedResult) {
  return refLabelsMap.map(
    ({key}) => formattedResult.find((entry) => entry.key === key)?.value ?? null
  );
}

function percentileOf(measure) {
  return measure?.aggregationType?.value ?? 0;
}

function bandLabel(measure) {
  return t('report.outlierBand.percentile', {value: percentileOf(measure)});
}
