/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Select} from 'components';
import {reportConfig} from 'services';
import equal from 'deep-equal';

import './ReportSelect.scss';
import {t} from 'translation';

export default function ReportSelect({
  type,
  field,
  value,
  disabled,
  onChange,
  variables,
  report,
  previous = []
}) {
  const config = reportConfig[type];
  const optionsWithoutVariables = config.options[field];

  const options = addVariables(optionsWithoutVariables, variables);
  const selectedOption = findSelectedOption(options, 'data', value);

  return (
    <Select
      onChange={value => {
        const foundOption = findSelectedOption(options, 'key', value);
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
                      : t(`report.${field}.${key.split('_')[1]}`)}
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
              {t(`report.${field}.${key}`)}
            </Select.Option>
          );
        }
      })}
    </Select>
  );
}

function addVariables(options, variables) {
  return options.map(option => {
    const subOptions = option.options;
    if (subOptions && typeof subOptions === 'string') {
      return {
        ...option,
        options: variables[subOptions].map(({id, name, type}) => {
          const value = id ? {id, name, type} : {name, type};
          return {
            key: subOptions + '_' + (id || name),
            label: name,
            data: {type: subOptions, value}
          };
        })
      };
    }
    return option;
  });
}

function findSelectedOption(options, compareProp, compareValue) {
  for (let i = 0; i < options.length; i++) {
    const option = options[i];
    if (option.options) {
      const found = findSelectedOption(option.options, compareProp, compareValue);
      if (found) {
        return found;
      }
    } else {
      if (equal(option[compareProp], compareValue, {strict: true})) {
        return option;
      }
    }
  }
}
