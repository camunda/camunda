/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createReportUpdate, reportConfig} from 'services';
import {Select} from 'components';

import './View.scss';

export default function View({report, onChange, variables}) {
  const views = reportConfig.view;
  const selectedOption = report.view ? views.find(({matcher}) => matcher(report)) : null;

  return (
    <Select
      className="View"
      onChange={(selection) => {
        let type = selection;
        let adjustment;

        if (selection.startsWith('variable_')) {
          type = 'variable';
          adjustment = {
            view: {
              properties: {
                $set: [variables.find(({name}) => name === selection.substr('variable_'.length))],
              },
            },
          };
        }

        onChange(createReportUpdate(report, 'view', type, adjustment));
      }}
      value={getValue(selectedOption?.key, report.view)}
      disabled={report.definitions.length === 0 || !report.definitions[0].key}
    >
      {views
        .filter(({visible, key}) => visible(report) && key !== 'none')
        .map(({key, enabled, label}) => {
          if (key === 'variable') {
            const numberVariables = variables?.filter(({type}) =>
              ['Float', 'Integer', 'Short', 'Long', 'Double'].includes(type)
            );

            return (
              <Select.Submenu
                key="variable"
                value="variable"
                label={label()}
                disabled={!enabled(report) || !numberVariables || !numberVariables?.length}
              >
                {numberVariables?.map(({name, label}, idx) => {
                  return <Select.Option key={idx} value={key + '_' + name} label={label || name} />;
                })}
              </Select.Submenu>
            );
          }
          return (
            <Select.Option key={key} value={key} disabled={!enabled(report)} label={label()} />
          );
        })}
    </Select>
  );
}

function getValue(selectedOption, view) {
  if (selectedOption === 'variable') {
    return 'variable_' + view.properties[0].name;
  }

  return selectedOption;
}
