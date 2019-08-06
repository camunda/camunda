/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {BPMNDiagram, TargetValueBadge, LoadingIndicator} from 'components';
import HeatmapOverlay from './HeatmapOverlay';

import {calculateTargetValueHeat} from './service';
import {formatters, getTooltipText} from 'services';

import './Heatmap.scss';
import {t} from 'translation';

const Heatmap = ({report, formatter, errorMessage}) => {
  const {
    result,
    data: {
      view: {property},
      configuration: {alwaysShowAbsolute, alwaysShowRelative, heatmapTargetValue: targetValue, xml}
    }
  } = report;

  const isDuration = property.toLowerCase().includes('duration');
  const alwaysShow = isDuration ? alwaysShowAbsolute : alwaysShowAbsolute || alwaysShowRelative;

  if (!result || typeof result.data !== 'object') {
    return <p>{errorMessage}</p>;
  }

  if (!xml) {
    return <LoadingIndicator />;
  }

  const resultObj = formatters.objectifyResult(result.data);

  let heatmapComponent;
  if (targetValue && targetValue.active && !targetValue.values.target) {
    const heat = calculateTargetValueHeat(resultObj, targetValue.values);
    heatmapComponent = [
      <HeatmapOverlay
        key="heatmap"
        data={heat}
        alwaysShow={alwaysShow}
        formatter={(_, id) => {
          const node = document.createElement('div');

          const target = formatters.convertToMilliseconds(
            targetValue.values[id].value,
            targetValue.values[id].unit
          );
          const real = resultObj[id];

          node.innerHTML = `${t('report.heatTarget.targetDuration')}: ${formatters.duration(
            target
          )}<br/>`;

          if (typeof real === 'number') {
            const relation = (real / target) * 100;

            node.innerHTML += t('report.heatTarget.actualDuration', {
              duration: formatters.duration(real),
              percentage: relation < 1 ? '< 1' : Math.round(relation)
            });
          } else {
            node.innerHTML += t('report.heatTarget.noValueAvailable');
          }

          // tooltips don't work well with spaces
          node.innerHTML = node.innerHTML.replace(/ /g, '\u00A0');

          return node;
        }}
      />,
      <TargetValueBadge key="targetValueBadge" values={targetValue.values} />
    ];
  } else {
    heatmapComponent = (
      <HeatmapOverlay
        data={resultObj}
        alwaysShow={alwaysShow}
        formatter={data =>
          getTooltipText(
            data,
            formatter,
            result.processInstanceCount,
            alwaysShowAbsolute,
            alwaysShowRelative,
            isDuration
          )
        }
      />
    );
  }

  return (
    <div className="Heatmap">
      <BPMNDiagram xml={xml}>{heatmapComponent}</BPMNDiagram>
    </div>
  );
};

export default Heatmap;
