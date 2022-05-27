/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useCallback, useEffect, useState} from 'react';
import fitty from 'fitty';

import {formatters, loadVariables, reportConfig} from 'services';
import {LoadingIndicator} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import ProgressBar from './ProgressBar';

import './Number.scss';

export function Number({report, formatter, mightFail}) {
  const {data, result, reportType} = report;
  const {targetValue, precision} = data.configuration;
  const [processVariable, setProcessVariable] = useState();
  const processVariableReport = reportType === 'process' && data.view.entity === 'variable';
  const isMultiMeasure = result?.measures.length > 1;

  useEffect(() => {
    // We need to load the variables in order to resolve the variable label
    // will be removed once OPT-5961 is done
    if (processVariableReport) {
      const {name, type} = data.view.properties[0];
      const payload =
        data.definitions?.map(({key, versions, tenantIds}) => ({
          processDefinitionKey: key,
          processDefinitionVersions: versions,
          tenantIds: tenantIds,
        })) || [];
      mightFail(
        loadVariables(payload),
        (variables) => {
          setProcessVariable(
            variables.find((variable) => variable.name === name && variable.type === type) || {}
          );
        },
        showError
      );
    }
  }, [data.definitions, processVariableReport, mightFail, data.view.properties]);

  const containerRef = useCallback((node) => {
    if (node) {
      fitty(node, {
        minSize: 5,
        maxSize: 55,
      });
    }
  }, []);

  if (targetValue && targetValue.active && !isMultiMeasure) {
    let min, max, isBelow;
    if (
      ['frequency', 'percentage'].includes(data.view.properties[0]) ||
      data.view.entity === 'variable'
    ) {
      min = targetValue.countProgress.baseline;
      max = targetValue.countProgress.target;
      isBelow = targetValue.countProgress.isBelow;
    } else {
      min = formatters.convertDurationToSingleNumber(targetValue.durationProgress.baseline);
      max = formatters.convertDurationToSingleNumber(targetValue.durationProgress.target);
      isBelow = targetValue.durationProgress.target.isBelow;
    }

    return (
      <ProgressBar
        min={min}
        max={max}
        isBelow={isBelow}
        value={result.data}
        formatter={formatter}
        precision={precision}
      />
    );
  }

  if (processVariableReport && !processVariable) {
    return <LoadingIndicator />;
  }

  return (
    <div className="Number">
      <div className="container" ref={containerRef}>
        {result.measures.map((measure, idx) => {
          let viewString;

          if (processVariableReport) {
            viewString = processVariable.label || data.view.properties[0].name;
          } else if (measure.property === 'percentage') {
            viewString = t('report.percentageOfInstances');
          } else {
            const config = reportConfig[reportType];
            const view = config.view.find(({matcher}) => matcher(data));
            let measureString = '';
            if (reportType === 'process') {
              measureString = t(
                'report.view.' + (measure.property === 'frequency' ? 'count' : 'duration')
              );
              if (view.key === 'incident' && measure.property === 'duration') {
                measureString = t('report.view.resolutionDuration');
              }
            }

            viewString = `${view.label()} ${measureString}`;
          }

          if (measure.property === 'duration' || data.view.entity === 'variable') {
            const {type, value} = measure.aggregationType;
            viewString += ' - ' + t('report.config.aggregationShort.' + type, {value});
          }

          const formatter =
            formatters[typeof measure.property === 'string' ? measure.property : 'frequency'];

          return (
            <React.Fragment key={idx}>
              <div className="data">{formatter(measure.data, precision)}</div>
              <div className="label">{viewString}</div>
            </React.Fragment>
          );
        })}
      </div>
    </div>
  );
}

export default withErrorHandling(Number);
