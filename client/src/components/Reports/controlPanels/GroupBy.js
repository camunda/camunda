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

import {addVariables, isDataEqual} from './service';

import './GroupBy.scss';

function GroupBy({type, value, onChange, variables, report, view}) {
  const config = reportConfig[type];
  const options = addVariables(config.options.groupBy, variables, (type, value) => ({type, value}));
  const noneOption = options.find(({data}) => data?.type === 'none');
  const distributedBy = report.data.distributedBy;

  const selectedOption = config.findSelectedOption(options, 'data', value);
  const hasGrouping = selectedOption?.data.type !== 'none';
  const canRemoveGrouping =
    hasGrouping &&
    (distributedBy?.type !== 'none' || config.isAllowed(report, view, noneOption.data));
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

  return (
    <li className="GroupBy select">
      <span className="label">{t(`report.groupBy.label`)}</span>
      <Select
        onChange={(value) => {
          const foundOption = config.findSelectedOption(options, 'key', value);
          onChange(foundOption.data);
        }}
        value={selectedOption ? selectedOption.key : null}
        label={!hasGrouping && `+ ${t('report.addGrouping')}`}
        className={classnames({
          hasNoGrouping: !hasGrouping,
          canRemoveGrouping,
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
      </Select>
      {canRemoveGrouping && (
        <Button
          className="removeGrouping"
          onClick={() => onChange(convertDistributionToGroup(distributedBy))}
        >
          <Icon type="close-small" />
        </Button>
      )}
    </li>
  );
}

export default React.memo(GroupBy, isDataEqual);

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
