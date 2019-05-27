/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import equal from 'deep-equal';

import {Dropdown} from 'components';
import {reportConfig} from 'services';

import './ReportDropdown.scss';

export default function ReportDropdown({
  type,
  field,
  value,
  disabled,
  onChange,
  variables,
  previous = []
}) {
  const config = reportConfig[type];
  const options = config.options[field];

  const label = config.getLabelFor(options, value) || 'Select...';

  return (
    <Dropdown label={label} className="ReportDropdown" disabled={disabled}>
      {options.map(({data, label, options}, idx) => {
        if (options) {
          if (typeof options === 'string') {
            options = variables[options].map(({id, name, type}) => {
              const value = id ? {id, name} : {name, type};
              return {
                label: name,
                data: {type: options, value}
              };
            });
          }

          return (
            <Dropdown.Submenu
              key={idx}
              checked={options.some(({data}) => equal(data, value, {strict: true}))}
              disabled={options.every(({data}) => !config.isAllowed(...previous, data))}
              label={label}
            >
              {options.map(({label, data}, idx) => {
                return (
                  <Dropdown.Option
                    disabled={!config.isAllowed(...previous, data)}
                    checked={equal(data, value, {strict: true})}
                    key={idx}
                    onClick={() => onChange(data)}
                  >
                    {label}
                  </Dropdown.Option>
                );
              })}
            </Dropdown.Submenu>
          );
        } else {
          return (
            <Dropdown.Option
              key={idx}
              checked={equal(data, value, {strict: true})}
              disabled={!config.isAllowed(...previous, data)}
              onClick={() => onChange(data)}
            >
              {label}
            </Dropdown.Option>
          );
        }
      })}
    </Dropdown>
  );
}
