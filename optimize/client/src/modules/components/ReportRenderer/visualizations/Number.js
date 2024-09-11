/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useCallback, useEffect, useState} from 'react';
import fitty from 'fitty';

import {formatters, loadVariables, reportConfig} from 'services';
import {Loading} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import {formatValue} from '../service';

import ProgressBar from './ProgressBar';

import './Number.scss';

export function Number({report, formatter, mightFail}) {
  const {data, result} = report;
  const {targetValue, precision} = data.configuration;
  const [processVariable, setProcessVariable] = useState();
  const processVariableReport = data.view.entity === 'variable';
  const isMultiMeasure = result?.measures.length > 1;

  useEffect(() => {
    // We need to load the variables in order to resolve the variable label
    // will be removed once OPT-5961 is done
    if (processVariableReport) {
      const {name, type} = data.view.properties[0];
      const payload = {
        processesToQuery:
          data.definitions?.map(({key, versions, tenantIds}) => ({
            processDefinitionKey: key,
            processDefinitionVersions: versions,
            tenantIds: tenantIds,
          })) || [],
        filter: data.filter,
      };
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
  }, [data.definitions, processVariableReport, mightFail, data.view.properties, data.filter]);

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
    return <Loading />;
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
            const view = reportConfig.view.find(({matcher}) => matcher(data));
            let measureString = '';
            measureString = t(
              'report.view.' + (measure.property === 'frequency' ? 'count' : 'duration')
            );
            if (view.key === 'incident' && measure.property === 'duration') {
              measureString = t('report.view.resolutionDuration');
            }

            viewString = `${view.label()} ${measureString}`;
          }

          if (measure.property === 'duration' || data.view.entity === 'variable') {
            const {type, value} = measure.aggregationType;
            viewString += ' - ' + t('report.config.aggregationShort.' + type, {value});
          }

          return (
            <React.Fragment key={idx}>
              <div className="data">{formatValue(measure.data, measure.property, precision)}</div>
              <div className="label">{viewString}</div>
            </React.Fragment>
          );
        })}
      </div>
    </div>
  );
}

export default withErrorHandling(Number);
