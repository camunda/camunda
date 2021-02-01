/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Select} from 'components';
import {reportConfig} from 'services';
import equal from 'deep-equal';
import update from 'immutability-helper';

import './ReportSelect.scss';
import {t} from 'translation';

function ReportSelect({type, field, value, disabled, onChange, variables, report, previous = []}) {
  const config = reportConfig[type];
  let options = config.options[field];

  if (field === 'groupBy') {
    options = addVariables(options, variables, (type, value) => ({type, value}));
  } else if (field === 'view') {
    options = addVariables(
      options,
      variables,
      (entity, property) => ({entity, property}),
      ({type}) => ['Float', 'Integer', 'Short', 'Long', 'Double'].includes(type)
    );

    if (type === 'process') {
      options = options.map((option) => {
        if (option.key === 'rawData' || option.key === 'variable') {
          return option;
        } else {
          return (
            option.options.find(({data}) => data.property === value?.property) || option.options[0]
          );
        }
      });
    }
  }

  const selectedOption = config.findSelectedOption(options, 'data', value);

  return (
    <Select
      onChange={(value) => {
        const foundOption = config.findSelectedOption(options, 'key', value);
        onChange(foundOption.data);
      }}
      value={selectedOption ? selectedOption.key : null}
      className="ReportSelect"
      disabled={disabled}
    >
      {options.map(({key, data, options}, idx) => {
        if (options) {
          return (
            <Select.Submenu
              key={idx}
              label={t(`report.${field}.${key}`)}
              disabled={options.every(({data}) => !config.isAllowed(report, ...previous, data))}
            >
              {options.map(({key, data, label}, idx) => {
                return (
                  <Select.Option
                    key={idx}
                    value={key}
                    disabled={!config.isAllowed(report, ...previous, data)}
                  >
                    {key.toLowerCase().includes('variable')
                      ? label
                      : t(`report.${field}.${key.split('_').pop()}`)}
                  </Select.Option>
                );
              })}
            </Select.Submenu>
          );
        } else {
          return (
            <Select.Option
              key={idx}
              value={key}
              disabled={!config.isAllowed(report, ...previous, data)}
            >
              {t(`report.${field}.${key.split('_').shift()}`)}
            </Select.Option>
          );
        }
      })}
    </Select>
  );
}

export default React.memo(ReportSelect, (prevProps, nextProps) => {
  const prevData = excludeConfig(prevProps.report.data);
  const nextData = excludeConfig(nextProps.report.data);

  if (equal(prevData, nextData) && equal(prevProps.variables, nextProps.variables)) {
    return true;
  }

  return false;
});

function excludeConfig(data) {
  return update(data, {$unset: ['configuration']});
}

function addVariables(options, variables, payloadFormatter, filter = () => true) {
  return options.map((option) => {
    const subOptions = option.options;
    if (subOptions && typeof subOptions === 'string') {
      return {
        ...option,
        options: variables[subOptions]?.filter(filter).map(({id, name, type}) => {
          const value = id ? {id, name, type} : {name, type};
          return {
            key: subOptions + '_' + (id || name),
            label: name,
            data: payloadFormatter(subOptions, value),
          };
        }),
      };
    }
    return option;
  });
}
