/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {color} from 'chart.js/helpers';
import deepEqual from 'fast-deep-equal';

const COLOR_FADE_OPACITY = 0.15;

export default function fadeOnHover({visualization, isStacked}) {
  let chartInstance = null;
  let canvas = null;
  let prevDatasets = [];

  function onHover(evt, datasets, chart) {
    if (deepEqual(prevDatasets, datasets)) {
      return;
    }
    const datasetIndexes = datasets.map((el) => el.datasetIndex);

    if (visualization === 'pie') {
      const dataIndexes = datasets.map((el) => el.index);
      chart.data.datasets.forEach((dataset, datasetIdx) => {
        dataset.hovered = false;
        dataset.backgroundColor = dataset.backgroundColor.map((color, dataIdx) => {
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
    prevDatasets = datasets;
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

  function onMouseMove({offsetX, offsetY}) {
    if (
      offsetX < chartInstance.chartArea.left ||
      chartInstance.chartArea.right < offsetX ||
      offsetY < chartInstance.chartArea.top ||
      chartInstance.chartArea.bottom < offsetY
    ) {
      // reset hover effect when moving mouse outside chart area
      onHover({}, [], chartInstance);
    }
  }

  return {
    afterInit: function (chart) {
      const originalHover = chart.options.onHover;
      chart.options.onHover = function (...args) {
        originalHover?.(...args);
        onHover.call(this, ...args);
      };
      chartInstance = chart;
      canvas = chart.canvas;

      canvas.addEventListener('mousemove', onMouseMove);
    },
    destroy: function () {
      canvas.removeEventListener('mousemove', onMouseMove);
    },
  };
}

function addAlpha(colorString, opacity) {
  return color(colorString).alpha(opacity).rgbString();
}
