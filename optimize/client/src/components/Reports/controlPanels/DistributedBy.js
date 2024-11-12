/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import classnames from 'classnames';
import {Close} from '@carbon/icons-react';
import {Button} from '@carbon/react';

import {t} from 'translation';
import {reportConfig, createReportUpdate} from 'services';
import {Select} from 'components';
import {useUiConfig} from 'hooks';

export default function DistributedBy({report, onChange, variables}) {
  const {userTaskAssigneeAnalyticsEnabled} = useUiConfig();

  if (!report.groupBy) {
    return null;
  }

  const distributions = reportConfig.distribution;
  const selectedOption = distributions.find(({matcher}) => matcher(report));
  const hasDistribution = selectedOption.key !== 'none';

  const options = distributions
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
            key="variable"
            label={label()}
            disabled={!enabled(report) || !variables || !variables?.length}
            openToLeft
          >
            {variables?.map?.(({name, label}, idx) => {
              return <Select.Option key={idx} value={key + '_' + name} label={label || name} />;
            })}
          </Select.Submenu>
        );
      } else if (['startDate', 'endDate'].includes(key)) {
        return (
          <Select.Submenu key={key} label={label()} disabled={!enabled(report)} openToLeft>
            <Select.Option value={key + '_automatic'} label={t('report.groupBy.automatic')} />
            <Select.Option value={key + '_year'} label={t('report.groupBy.year')} />
            <Select.Option value={key + '_month'} label={t('report.groupBy.month')} />
            <Select.Option value={key + '_week'} label={t('report.groupBy.week')} />
            <Select.Option value={key + '_day'} label={t('report.groupBy.day')} />
            <Select.Option value={key + '_hour'} label={t('report.groupBy.hour')} />
          </Select.Submenu>
        );
      }
      return <Select.Option key={key} value={key} disabled={!enabled(report)} label={label()} />;
    });

  if (!options.length) {
    return null;
  }

  return (
    <li className="DistributedBy GroupBy">
      <span className="label">{t('common.and')}</span>
      <Select
        className={classnames({hasNoGrouping: !hasDistribution})}
        label={!hasDistribution && '+ ' + t('report.addGrouping')}
        onChange={(selection) => {
          let type = selection,
            value = null;
          if (selection.startsWith('variable_')) {
            type = 'variable';
            value = variables.find(({name}) => name === selection.substr('variable_'.length));
          } else if (selection.startsWith('startDate') || selection.startsWith('endDate')) {
            [type, value] = selection.split('_');
            value = {unit: value};
          }

          onChange(
            createReportUpdate(report, 'distribution', type, {
              distributedBy: {value: {$set: value}},
            })
          );
        }}
        value={getValue(selectedOption.key, report.distributedBy)}
      >
        {options}
      </Select>
      {hasDistribution && (
        <Button
          size="sm"
          kind="ghost"
          iconDescription={t('common.reset')}
          className="removeGrouping"
          onClick={() => onChange(createReportUpdate(report, 'distribution', 'none'))}
          hasIconOnly
          renderIcon={Close}
        ></Button>
      )}
    </li>
  );
}

function getValue(selectedOption, distributedBy) {
  if (selectedOption === 'variable') {
    return 'variable_' + distributedBy.value.name;
  }
  if (['startDate', 'endDate'].includes(selectedOption)) {
    return selectedOption + '_' + distributedBy.value.unit;
  }

  return selectedOption;
}
