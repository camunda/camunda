/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {
  Icon,
  BPMNDiagram,
  TargetValueBadge,
  Button,
  LoadingIndicator,
  HeatmapOverlay,
} from 'components';

import {getConfig, calculateTargetValueHeat} from './service';
import {loadRawData, formatters, getTooltipText} from 'services';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';

import './Heatmap.scss';

export function Heatmap({report, formatter, mightFail, context}) {
  const {
    name,
    result,
    data: {
      view: {properties},
      configuration: {
        alwaysShowAbsolute,
        alwaysShowRelative,
        heatmapTargetValue: targetValue,
        xml,
        aggregationType,
      },
    },
  } = report;

  const isDuration = properties[0].toLowerCase().includes('duration');
  const alwaysShow = isDuration ? alwaysShowAbsolute : alwaysShowAbsolute || alwaysShowRelative;

  if (!xml || !result) {
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

          tooltipHTML = `${t('report.heatTarget.targetDuration')}: <b>${formatters.duration(
            target
          )}</b><br/>`;

          if (typeof real === 'number') {
            const relation = (real / target) * 100;

            tooltipHTML +=
              t(`report.heatTarget.duration.${aggregationType}`) +
              t('report.heatTarget.actualDuration', {
                duration: formatters.duration(real),
                percentage: relation < 1 ? '< 1' : Math.round(relation),
              });
          } else {
            tooltipHTML += t('report.heatTarget.noValueAvailable');
          }

          // tooltips don't work well with spaces
          tooltipHTML = tooltipHTML.replace(/ /g, '\u00A0');

          return (
            <div>
              <span className="text" dangerouslySetInnerHTML={{__html: tooltipHTML}} />
              {context !== 'shared' && (
                <Button
                  onClick={async () => {
                    mightFail(
                      loadRawData(getConfig(report.data, id)),
                      (data) => {
                        const hiddenElement = document.createElement('a');
                        hiddenElement.href = window.URL.createObjectURL(data);
                        hiddenElement.download =
                          t('report.heatTarget.exceededInstances', {
                            name: formatters.formatFileName(name),
                          }) + '.csv';
                        hiddenElement.click();
                      },
                      showError
                    );
                  }}
                >
                  <Icon type="save" />
                  {t('common.instanceIds')}
                </Button>
              )}
            </div>
          );
        }}
      />,
      <TargetValueBadge key="targetValueBadge" values={targetValue.values} />,
    ];
  } else {
    heatmapComponent = (
      <HeatmapOverlay
        data={resultObj}
        tooltipOptions={{alwaysShow}}
        formatter={(data) =>
          getTooltipText(
            data,
            formatter,
            result.instanceCount,
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
}

export default withErrorHandling(Heatmap);
