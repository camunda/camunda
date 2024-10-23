/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {color} from 'chart.js/helpers';
import deepEqual from 'fast-deep-equal';

const COLOR_FADE_OPACITY = 0.15;

export default function fadeOnHover({visualization, isStacked}) {
  let prevDatasets = [];
  let delayTimer;
  let isHoverDelayElapsed = false;
  let lastUpdater;

  function onHover(_evt, datasets, chart) {
    if (deepEqual(prevDatasets, datasets)) {
      return;
    }

    const isHoveringStarted = prevDatasets.length === 0 && datasets.length > 0;
    const isHoveringEnded = prevDatasets.length > 0 && datasets.length === 0;

    if (isHoveringStarted) {
      delayTimer = setTimeout(() => {
        isHoverDelayElapsed = true;
        // if we call updateChartColors directly here,
        // setimeout will create a closure on an old version of the updateChartColors
        // thats why, we call a function the stores the latest version of updateChartColors
        lastUpdater();
      }, 350);
    }

    if (isHoveringEnded) {
      updateChartColors(datasets, chart);
      clearTimeout(delayTimer);
      isHoverDelayElapsed = false;
    }

    if (isHoverDelayElapsed) {
      updateChartColors(datasets, chart);
    }

    lastUpdater = () => updateChartColors(datasets, chart);
    prevDatasets = datasets;
  }

  function updateChartColors(datasets, chart) {
    const datasetIndexes = datasets.map((el) => el.datasetIndex);

    if (visualization === 'pie') {
      const dataIndexes = datasets.map((el) => el.index);
      chart.data.datasets.forEach((dataset, datasetIdx) => {
        dataset.hovered = false;
        dataset.backgroundColor = dataset.backgroundColor.map((_color, dataIdx) => {
          if (
            datasets.length === 0 ||
            (datasetIndexes.includes(datasetIdx) && dataIndexes.includes(dataIdx))
          ) {
            dataset.hovered = true;
            return getOriginal(dataset, 'backgroundColor')[dataIdx];
          } else {
            return getFaded(dataset, 'backgroundColor')[dataIdx];
          }
        });
      });
    } else {
      const datasetStacks = datasets.map((el) => chart.data.datasets[el.datasetIndex].stack);
      chart.data.datasets.forEach((dataset, i) => {
        dataset.hovered = true;
        let getColor = getFaded;
        if (
          datasets.length === 0 ||
          datasetIndexes.includes(i) ||
          (isStacked && dataset.type !== 'line' && datasetStacks.includes(dataset.stack))
        ) {
          dataset.hovered = false;
          getColor = getOriginal;
        }

        dataset.backgroundColor = getColor(dataset, 'backgroundColor');
        dataset.borderColor = getColor(dataset, 'borderColor');
        if (dataset.legendColor) {
          dataset.legendColor = getColor(dataset, 'legendColor');
        }
        if (dataset.pointBorderColor) {
          dataset.pointBorderColor = getColor(dataset, 'pointBorderColor');
        }
      });
    }
    chart.update();
  }

  function getOriginal(dataset, property) {
    if (!dataset['original-' + property]) {
      dataset['original-' + property] = dataset[property];
    }

    return dataset['original-' + property];
  }

  function getFaded(dataset, property) {
    const originalColor = getOriginal(dataset, property);
    if (Array.isArray(originalColor)) {
      return originalColor.map((color) => {
        if (typeof color === 'string') {
          return addAlpha(color, COLOR_FADE_OPACITY);
        } else {
          // get pattern color
          return addAlpha(color.color, COLOR_FADE_OPACITY);
        }
      });
    }

    return addAlpha(originalColor, COLOR_FADE_OPACITY);
  }

  return {
    afterInit: function (chart) {
      const originalHover = chart.options.onHover;
      chart.options.onHover = function (...args) {
        originalHover?.(...args);
        onHover.call(this, ...args);
      };
    },
    afterEvent(chart, {event}) {
      if (event.type === 'mouseout') {
        onHover({}, [], chart);
      }
    },
  };
}

function addAlpha(colorString, opacity) {
  return color(colorString).alpha(opacity).rgbString();
}
