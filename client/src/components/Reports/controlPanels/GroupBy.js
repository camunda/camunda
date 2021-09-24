/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useState, useEffect} from 'react';
import classnames from 'classnames';

import {t} from 'translation';
import {reportConfig, updateReport} from 'services';
import {Select, Button, Icon} from 'components';
import {isOptimizeCloudEnvironment} from 'config';

import './GroupBy.scss';

export default function GroupBy({type, report, onChange, variables}) {
  const [isOptimizeCloud, setIsOptimizeCloud] = useState(true);

  useEffect(() => {
    (async () => {
      setIsOptimizeCloud(await isOptimizeCloudEnvironment());
    })();
  }, []);

  const reportType = type;

  if (!report.groupBy) {
    return null;
  }

  const groups = reportConfig[type].group;
  const selectedOption = groups.find(({matcher}) => matcher(report));
  const hasGroup = selectedOption.key !== 'none';
  let hasDistribution;

  if (type === 'decision') {
    hasDistribution = false;
  } else {
    hasDistribution =
      reportConfig[type].distribution.find(({matcher}) => matcher(report)).key !== 'none';
  }

  const options = groups
    .filter(
      ({visible, key}) =>
        visible(report) &&
        key !== 'none' &&
        (isOptimizeCloud ? !['assignee', 'candidateGroup'].includes(key) : true)
    )
    .map(({key, enabled, label}) => {
      if (['variable', 'inputVariable', 'outputVariable'].includes(key)) {
        return (
          <Select.Submenu
            key={key}
            label={label()}
            disabled={!enabled(report) || !variables || !variables[key]?.length}
          >
            {variables?.[key]?.map(({name}, idx) => {
              return (
                <Select.Option key={idx} value={key + '_' + name}>
                  {name}
                </Select.Option>
              );
            })}
          </Select.Submenu>
        );
      } else if (['startDate', 'endDate', 'runningDate', 'evaluationDate'].includes(key)) {
        return (
          <Select.Submenu key={key} label={label()} disabled={!enabled(report)}>
            <Select.Option value={key + '_automatic'}>
              {t('report.groupBy.automatic')}
            </Select.Option>
            <Select.Option value={key + '_year'}>{t('report.groupBy.year')}</Select.Option>
            <Select.Option value={key + '_month'}>{t('report.groupBy.month')}</Select.Option>
            <Select.Option value={key + '_week'}>{t('report.groupBy.week')}</Select.Option>
            <Select.Option value={key + '_day'}>{t('report.groupBy.day')}</Select.Option>
            <Select.Option value={key + '_hour'}>{t('report.groupBy.hour')}</Select.Option>
          </Select.Submenu>
        );
      }
      return (
        <Select.Option key={key} value={key} disabled={!enabled(report)}>
          {label()}
        </Select.Option>
      );
    });

  if (options.every(({props}) => props.disabled)) {
    return null;
  }

  return (
    <li className="GroupBy">
      <span className="label">{t('report.groupBy.label')}</span>
      <Select
        className={classnames({hasNoGrouping: !hasGroup})}
        label={!hasGroup && '+ Add grouping'}
        onChange={(selection) => {
          let type = selection,
            value = null;
          if (
            selection.startsWith('variable_') ||
            selection.startsWith('inputVariable_') ||
            selection.startsWith('outputVariable_')
          ) {
            [type, value] = selection.split('_');
            value = variables[type].find(({name}) => name === selection.substr(type.length + 1));
          } else if (
            selection.startsWith('startDate') ||
            selection.startsWith('endDate') ||
            selection.startsWith('runningDate') ||
            selection.startsWith('evaluationDate')
          ) {
            [type, value] = selection.split('_');
            value = {unit: value};
          }

          onChange(
            updateReport(reportType, report, 'group', type, {groupBy: {value: {$set: value}}})
          );
        }}
        value={getValue(selectedOption.key, report.groupBy)}
      >
        {options}
      </Select>
      {((hasGroup && groups.find(({key}) => key === 'none').enabled(report)) ||
        hasDistribution) && (
        <Button
          className="removeGrouping"
          onClick={() =>
            onChange(
              updateReport(reportType, report, 'group', 'none', {
                groupBy: {
                  $set:
                    selectedOption.key === 'process'
                      ? {type: 'none', value: null}
                      : convertDistributionToGroup(report.distributedBy),
                },
                distributedBy: {$set: {type: 'none', value: null}},
              })
            )
          }
        >
          <Icon type="close-small" />
        </Button>
      )}
    </li>
  );
}

function getValue(selectedOption, groupBy) {
  if (['variable', 'inputVariable', 'outputVariable'].includes(selectedOption)) {
    return selectedOption + '_' + groupBy.value.name;
  }
  if (['startDate', 'endDate', 'runningDate', 'evaluationDate'].includes(selectedOption)) {
    return selectedOption + '_' + groupBy.value.unit;
  }

  return selectedOption;
}

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
