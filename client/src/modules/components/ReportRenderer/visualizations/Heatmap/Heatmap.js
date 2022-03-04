/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';

import {
  Icon,
  BPMNDiagram,
  TargetValueBadge,
  LoadingIndicator,
  HeatmapOverlay,
  Select,
  DownloadButton,
} from 'components';
import {loadRawData, formatters, getTooltipText, processResult} from 'services';
import {t} from 'translation';

import {getConfig, calculateTargetValueHeat} from './service';

import './Heatmap.scss';

export function Heatmap({report, context}) {
  const [selectedMeasure, setSelectedMeasure] = useState(0);

  const {
    name,
    result,
    data: {
      view: {properties},
      configuration: {alwaysShowAbsolute, alwaysShowRelative, heatmapTargetValue: targetValue, xml},
    },
  } = report;

  const isDuration = properties[0].toLowerCase().includes('duration');
  const alwaysShow = isDuration ? alwaysShowAbsolute : alwaysShowAbsolute || alwaysShowRelative;

  if (!xml || !result) {
    return <LoadingIndicator />;
  }

  const resultObj = formatters.objectifyResult(
    processResult({...report, result: result.measures[selectedMeasure]}).data
  );

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
            const type = result.measures[selectedMeasure].aggregationType?.type;

            tooltipHTML +=
              t(`report.heatTarget.duration.${type}`) +
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
                <DownloadButton
                  retriever={loadRawData(getConfig(report.data, id))}
                  fileName={
                    t('report.heatTarget.exceededInstances', {
                      name: formatters.formatFileName(name),
                    }) + '.csv'
                  }
                  totalCount={result.instanceCount}
                >
                  <Icon type="save" />
                  {t('common.instanceIds')}
                </DownloadButton>
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
        formatter={(data, id) => {
          if (
            result.measures.every(
              (measure) => measure.data.find((entry) => entry.key === id)?.value === null
            )
          ) {
            // do not show tooltip for elements that have no data in all included measures
            return;
          }

          return (
            <table>
              <tbody>
                {result.measures.map((measure, idx) => {
                  return (
                    <tr key={idx}>
                      <td>
                        <b>{getMeasureString(measure)}:</b>
                      </td>
                      <td>
                        {getTooltipText(
                          measure.data.find((entry) => entry.key === id)?.value,
                          formatters[measure.property],
                          result.instanceCount,
                          alwaysShowAbsolute,
                          alwaysShowRelative,
                          measure.property === 'duration'
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          );
        }}
      />
    );
  }

  return (
    <div className="Heatmap">
      <BPMNDiagram xml={xml}>{heatmapComponent}</BPMNDiagram>
      {result.measures.length > 1 && (
        <Select value={selectedMeasure} onChange={(measure) => setSelectedMeasure(+measure)}>
          {result.measures.map((measure, idx) => {
            return (
              <Select.Option value={idx} key={idx}>
                Heat: {getMeasureString(measure)}
              </Select.Option>
            );
          })}
        </Select>
      )}
    </div>
  );
}

export default Heatmap;

function getMeasureString(measure) {
  let property = measure.property;
  if (property === 'frequency') {
    property = 'count';
  }
  const {aggregationType: aggregation, userTaskDurationTime} = measure;

  return (
    t('report.view.' + property) +
    (aggregation
      ? ` - ${t('report.config.aggregationShort.' + aggregation.type, {value: aggregation.value})}`
      : '') +
    (userTaskDurationTime
      ? ` (${t('report.config.userTaskDuration.' + userTaskDurationTime)})`
      : '')
  );
}
