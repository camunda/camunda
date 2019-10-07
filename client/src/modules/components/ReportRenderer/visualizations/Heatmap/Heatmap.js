/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {BPMNDiagram, TargetValueBadge, LoadingIndicator, HeatmapOverlay} from 'components';

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
        tooltipOptions={{alwaysShow}}
        formatter={(_, id) => {
          let tooltipHTML = '';

          const target = formatters.convertToMilliseconds(
            targetValue.values[id].value,
            targetValue.values[id].unit
          );
          const real = resultObj[id];

          tooltipHTML = `${t('report.heatTarget.targetDuration')}: ${formatters.duration(
            target
          )}<br/>`;

          if (typeof real === 'number') {
            const relation = (real / target) * 100;

            tooltipHTML += t('report.heatTarget.actualDuration', {
              duration: formatters.duration(real),
              percentage: relation < 1 ? '< 1' : Math.round(relation)
            });
          } else {
            tooltipHTML += t('report.heatTarget.noValueAvailable');
          }

          // tooltips don't work well with spaces
          tooltipHTML = tooltipHTML.replace(/ /g, '\u00A0');

          return <span dangerouslySetInnerHTML={{__html: tooltipHTML}} />;
        }}
      />,
      <TargetValueBadge key="targetValueBadge" values={targetValue.values} />
    ];
  } else {
    heatmapComponent = (
      <HeatmapOverlay
        data={resultObj}
        tooltipOptions={{alwaysShow}}
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
