/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';

import {
  BPMNDiagram,
  TargetValueBadge,
  Loading,
  HeatmapOverlay,
  Select,
  DownloadButton,
} from 'components';
import {loadRawData, formatters, getTooltipText, processResult} from 'services';
import {t} from 'translation';
import {useUser} from 'hooks';

import {getConfig, calculateTargetValueHeat} from './service';

import './Heatmap.scss';

export default function Heatmap({report, context}) {
  const [selectedMeasure, setSelectedMeasure] = useState(0);
  const {user} = useUser();
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
        precision,
      },
    },
  } = report;

  const isDuration = properties[0].toLowerCase().includes('duration');
  const alwaysShow = isDuration ? alwaysShowAbsolute : alwaysShowAbsolute || alwaysShowRelative;

  if (!xml || !result) {
    return <Loading />;
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
          const target = formatters.convertToMilliseconds(
            targetValue.values[id].value,
            targetValue.values[id].unit
          );
          const real = resultObj[id];
          let tooltipHTML;

          const targetDuration = (
            <>
              {t('report.heatTarget.targetDuration')}:{' '}
              <b>{formatters.duration(target, precision, alwaysShow)}</b>
              <br />
            </>
          );

          if (typeof real === 'number') {
            const relation = (real / target) * 100;
            const {type, value} = result.measures[selectedMeasure].aggregationType || {};
            if (type) {
              tooltipHTML = (
                <>
                  {targetDuration}
                  {t(`report.heatTarget.duration.${type}`, {value})}
                  {t('report.heatTarget.actualDuration', {
                    duration: formatters.duration(real, precision, alwaysShow),
                    percentage: relation < 1 ? '< 1' : Math.round(relation),
                  })}
                </>
              );
            }
          } else {
            tooltipHTML = (
              <>
                {targetDuration}
                {t('report.heatTarget.noValueAvailable')}
              </>
            );
          }

          return (
            <div>
              <span className="text">{tooltipHTML}</span>
              {context !== 'shared' && (
                <DownloadButton
                  kind="secondary"
                  size="sm"
                  retriever={() => loadRawData(getConfig(report.data, id))}
                  fileName={
                    t('report.heatTarget.exceededInstances', {
                      name: formatters.formatFileName(name),
                    }) + '.csv'
                  }
                  totalCount={result.instanceCount}
                  user={user}
                >
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
        formatter={(_data, id) => {
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
                  const measureString = getMeasureString(measure, alwaysShow);
                  return (
                    <tr key={idx}>
                      <td>{measureString ? <b>{measureString}:</b> : ''}</td>
                      <td>
                        {getTooltipText(
                          measure.data.find((entry) => entry.key === id)?.value,
                          formatters[measure.property],
                          result.instanceCount,
                          alwaysShowAbsolute,
                          alwaysShowRelative,
                          measure.property === 'duration',
                          precision,
                          alwaysShow
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
              <Select.Option value={idx} key={idx} label={`Heat: ${getMeasureString(measure)}`} />
            );
          })}
        </Select>
      )}
    </div>
  );
}

function getMeasureString(measure, shortNotation) {
  let property = measure.property;
  if (property === 'frequency') {
    property = 'count';
  }
  const {aggregationType: aggregation, userTaskDurationTime} = measure;

  let measureString = '';
  if (shortNotation) {
    // for short notation we want to show property name just for count measures
    measureString = property === 'count' ? t('report.view.count') : '';
  } else {
    measureString = t('report.view.' + property) + (aggregation ? ' - ' : '');
  }

  return (
    measureString +
    (aggregation
      ? t('report.config.aggregationShort.' + aggregation.type, {value: aggregation.value})
      : '') +
    (userTaskDurationTime
      ? ` (${t('report.config.userTaskDuration.' + userTaskDurationTime)})`
      : '')
  );
}
