/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {Button, Select, Icon} from 'components';
import {reportConfig} from 'services';
import {t} from 'translation';

import {addVariables} from './service';

import './GroupBy.scss';

function GroupBy({type, value, onChange, variables, report, view}) {
  const config = reportConfig[type];
  const options = addVariables(config.options.groupBy, variables, (type, value) => ({type, value}));
  const noneOption = options.find(({data}) => data?.type === 'none');
  const {distributedBy, configuration} = report.data;

  const selectedOption = config.findSelectedOption(options, 'data', value);
  const hasGrouping = selectedOption?.data.type !== 'none';

  const canAddGrouping = options.some(({data, options}) => {
    if (data?.type === 'none') {
      return false;
    }
    if (options) {
      return options.some(({data}) => config.isAllowed(report, view, data));
    } else {
      return config.isAllowed(report, view, data);
    }
  });

  if (!view || !canAddGrouping) {
    return null;
  }

  const isNoneAllowed = config.isAllowed(report, view, noneOption.data);
  const isGroupByProcessAllowed =
    !['incident', 'variable', null].includes(view.entity) && // group by process not allowed for incident, variable and raw data reports
    isNoneAllowed && // if group by none is not allowed, group by process is handled in distributeBy component instead
    report.data.definitions.length > 1; // group by process only allowed for multi-definition reports

  function canRemoveGrouping() {
    if (hasGrouping && isNoneAllowed) {
      return true;
    }

    if (!hasGrouping && distributedBy?.type === 'process' && isNoneAllowed) {
      return true;
    }

    if (hasGrouping && distributedBy?.type !== 'none' && distributedBy?.type !== 'process') {
      return true;
    }

    return false;
  }

  return (
    <li className="GroupBy select">
      <span className="label">{t(`report.groupBy.label`)}</span>
      <Select
        onChange={(value) => {
          if (value === 'process') {
            const changes = config.update('groupBy', noneOption.data, {report});
            changes.distributedBy = {$set: {type: 'process', value: null}};
            changes.visualization = {$set: 'table'};
            changes.configuration.xLabel = {$set: 'Process'};

            if (configuration.aggregationTypes.includes('median')) {
              if (configuration.aggregationTypes.length === 1) {
                changes.configuration.aggregationTypes = {$set: ['avg']};
              } else {
                changes.configuration.aggregationTypes = {
                  $set: configuration.aggregationTypes.filter((entry) => entry !== 'median'),
                };
              }
            }

            onChange(changes, true);
          } else {
            const foundOption = config.findSelectedOption(options, 'key', value);
            onChange(config.update('groupBy', foundOption.data, {report}), true);
          }
        }}
        value={!hasGrouping && distributedBy?.type === 'process' ? 'process' : selectedOption?.key}
        label={!hasGrouping && distributedBy?.type !== 'process' && `+ ${t('report.addGrouping')}`}
        className={classnames({
          hasNoGrouping: !hasGrouping && distributedBy?.type !== 'process',
          canRemoveGrouping: canRemoveGrouping(),
        })}
      >
        {options
          .filter(({data}) => data?.type !== 'none')
          .map(({key, data, options}, idx) => {
            if (options) {
              return (
                <Select.Submenu
                  key={idx}
                  label={t(`report.groupBy.${key}`)}
                  disabled={options.every(({data}) => !config.isAllowed(report, view, data))}
                >
                  {options.map(({key, data, label}, idx) => {
                    return (
                      <Select.Option
                        key={idx}
                        value={key}
                        disabled={!config.isAllowed(report, view, data)}
                      >
                        {key.toLowerCase().includes('variable')
                          ? label
                          : t(`report.groupBy.${key.split('_').pop()}`)}
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
                  disabled={!config.isAllowed(report, view, data)}
                >
                  {t(`report.groupBy.${key.split('_').shift()}`)}
                </Select.Option>
              );
            }
          })}
        {isGroupByProcessAllowed && (
          <Select.Option value="process">{t('common.process.label')}</Select.Option>
        )}
      </Select>
      {canRemoveGrouping() && (
        <Button
          className="removeGrouping"
          onClick={() => {
            if (distributedBy?.type === 'process') {
              const changes = config.update('groupBy', noneOption.data, {report});
              if (hasGrouping) {
                changes.distributedBy = {$set: {type: 'process', value: null}};
                changes.visualization = {$set: 'table'};
              } else {
                changes.distributedBy = {$set: {type: 'none', value: null}};
                changes.visualization = {$set: 'number'};
              }
              onChange(changes, true);
            } else {
              onChange(
                config.update('groupBy', convertDistributionToGroup(distributedBy), {report}),
                true
              );
            }
          }}
        >
          <Icon type="close-small" />
        </Button>
      )}
    </li>
  );
}

export default GroupBy;

function convertDistributionToGroup(distributedBy) {
  switch (distributedBy.type) {
    case 'flowNode':
      return {type: 'flowNodes', value: null};
    case 'userTask':
      return {type: 'userTasks', value: null};

    default:
      return distributedBy;
  }
}
