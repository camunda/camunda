/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import classnames from 'classnames';
import {Button} from '@carbon/react';
import {Close} from '@carbon/icons-react';

import {t} from 'translation';
import {reportConfig, createReportUpdate} from 'services';
import {Select} from 'components';
import {useUiConfig} from 'hooks';

import './GroupBy.scss';

export default function GroupBy({report, onChange, variables}) {
  const {userTaskAssigneeAnalyticsEnabled} = useUiConfig();

  if (!report.view) {
    return null;
  }

  const groups = reportConfig.group;
  const selectedOption = report.groupBy ? groups.find(({matcher}) => matcher(report)) : {key: null};
  const hasGroup = selectedOption.key !== 'none';
  const hasDistribution =
    reportConfig.distribution.find(({matcher}) => matcher(report)).key !== 'none';

  const options = groups
    .filter(
      ({visible, key}) =>
        visible(report) &&
        key !== 'none' &&
        (userTaskAssigneeAnalyticsEnabled || key !== 'assignee')
    )
    .map(({key, enabled, label}) => {
      if (key === 'variable') {
        return (
          <Select.Submenu
            key={key}
            label={label()}
            disabled={!enabled(report) || !variables || !variables[key]?.length}
          >
            {variables?.[key]?.map(({name, label}, idx) => {
              return <Select.Option key={idx} value={key + '_' + name} label={label || name} />;
            })}
          </Select.Submenu>
        );
      } else if (['startDate', 'endDate', 'runningDate', 'evaluationDate'].includes(key)) {
        return (
          <Select.Submenu key={key} label={label()} disabled={!enabled(report)}>
            <Select.Option label={t('report.groupBy.automatic')} value={key + '_automatic'} />
            <Select.Option label={t('report.groupBy.year')} value={key + '_year'} />
            <Select.Option label={t('report.groupBy.month')} value={key + '_month'} />
            <Select.Option label={t('report.groupBy.week')} value={key + '_week'} />
            <Select.Option label={t('report.groupBy.day')} value={key + '_day'} />
            <Select.Option label={t('report.groupBy.hour')} value={key + '_hour'} />
          </Select.Submenu>
        );
      }
      return <Select.Option key={key} value={key} disabled={!enabled(report)} label={label()} />;
    });

  if (options.every(({props}) => props.disabled)) {
    return null;
  }

  return (
    <li className="GroupBy">
      <span className="label">{t('report.groupBy.label')}</span>
      <Select
        className={classnames({hasNoGrouping: !hasGroup})}
        label={!hasGroup && '+ ' + t('report.addGrouping')}
        onChange={(selection) => {
          let type = selection,
            value = null;
          if (selection.startsWith('variable_')) {
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

          onChange(createReportUpdate(report, 'group', type, {groupBy: {value: {$set: value}}}));
        }}
        value={getValue(selectedOption.key, report.groupBy)}
      >
        {options}
      </Select>
      {((hasGroup && groups.find(({key}) => key === 'none').enabled(report)) ||
        hasDistribution) && (
        <Button
          size="sm"
          kind="ghost"
          iconDescription={t('common.reset')}
          className="removeGrouping"
          onClick={() =>
            onChange(
              createReportUpdate(report, 'group', 'none', {
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
          hasIconOnly
          renderIcon={Close}
        />
      )}
    </li>
  );
}

function getValue(selectedOption, groupBy) {
  if (selectedOption === 'variable') {
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
