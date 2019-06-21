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

export default function ReportSelect({
  type,
  field,
  value,
  disabled,
  onChange,
  variables,
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
      {options.map(({key, data, label, options}, idx) => {
        if (options) {
          return (
            <Select.Submenu
              key={idx}
              label={label}
              disabled={options.every(({data}) => !config.isAllowed(...previous, data))}
            >
              {options.map(({key, data, label}, idx) => {
                return (
                  <Select.Option
                    key={idx}
                    value={key}
                    disabled={!config.isAllowed(...previous, data)}
                  >
                    {label}
                  </Select.Option>
                );
              })}
            </Select.Submenu>
          );
        } else {
          return (
            <Select.Option key={idx} value={key} disabled={!config.isAllowed(...previous, data)}>
              {label}
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
          const value = id ? {id, name} : {name, type};
          return {
            key: 'variable_' + (id || name),
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
