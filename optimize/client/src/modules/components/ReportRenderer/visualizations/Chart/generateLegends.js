/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {t} from 'translation';

let previouslyTruncated = {};
let viewMoreClicked = {};
let originalGenerateLabels;

function draw(chart) {
  if (!chart.options.plugins.legend.autoCollapse) {
    return;
  }

  const chartRadius = chart._metasets[0].controller.outerRadius || 0;
  if (chart.legend.height > chartRadius && !viewMoreClicked[chart.id]) {
    chart.options.plugins.legend.maxHeight = 100;
    chart.options.plugins.legend.labels.generateLabels = function (chart) {
      // add an invisible legend to catch onClick and onHover easily
      return originalGenerateLabels(chart)
        .slice(0, 3)
        .concat([emptyLegend('viewMoreLegend')]);
    };

    update(chart, true);
  }

  // expand the chart legends when clicking "View More"
  if (previouslyTruncated[chart.id] && viewMoreClicked[chart.id]) {
    chart.options.plugins.legend.maxHeight = undefined;
    chart.options.plugins.legend.labels.generateLabels = (chart) =>
      [emptyLegend('viewLessLegend')].concat(originalGenerateLabels(chart));
    update(chart, false);
  }

  if (previouslyTruncated[chart.id]) {
    addText(chart, 3, t('common.viewMore'));
  } else if (viewMoreClicked[chart.id]) {
    addText(chart, 0, t('common.viewLess'));
  }
}

function update(chart, newTruncatedState) {
  if (previouslyTruncated[chart.id] === newTruncatedState) {
    return;
  }

  previouslyTruncated[chart.id] = newTruncatedState;
  chart.update();
}

function addText(chart, position, text) {
  const {left, width, top} = chart.legend.legendHitBoxes[position];
  const textXPos = left + width / 2 - 20;
  const textYPos = top + 5;
  chart.ctx.fillStyle = 'blue';
  chart.ctx.fillText(text, textXPos, textYPos);
}

function emptyLegend(text) {
  return {
    text,
    fillStyle: 'tranparent',
    strokeStyle: 'transparent',
    fontColor: 'transparent',
    boxWidth: 10,
  };
}

const generateLegends = {
  beforeDraw: draw,
  onResize: draw,
  beforeInit: function (chart) {
    if (!chart.options.plugins.legend.autoCollapse) {
      return;
    }

    originalGenerateLabels = chart.options.plugins.legend.labels.generateLabels;

    chart.options.plugins.legend.onClick = (evt, legend) => {
      if (legend.text === 'viewMoreLegend') {
        viewMoreClicked[chart.id] = true;
        chart.update();
      }

      if (legend.text === 'viewLessLegend') {
        viewMoreClicked[chart.id] = false;
        chart.update();
      }
    };

    chart.options.plugins.legend.onHover = (evt, legend) => {
      if (legend.text === 'viewMoreLegend' || legend.text === 'viewLessLegend') {
        chart.canvas.style.cursor = 'pointer';
      } else {
        chart.canvas.style.cursor = 'auto';
      }
    };

    chart.options.plugins.legend.onLeave = (evt, legend) => {
      chart.canvas.style.cursor = 'auto';
    };
  },
};

export default generateLegends;
