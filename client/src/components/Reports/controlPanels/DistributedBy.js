/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import classnames from 'classnames';
import {useState, useEffect} from 'react';

import {t} from 'translation';
import {reportConfig, createReportUpdate} from 'services';
import {Select, Button, Icon} from 'components';
import {getOptimizeProfile} from 'config';

export default function DistributedBy({report, onChange, variables}) {
  const [optimizeProfile, setOptimizeProfile] = useState();

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
    })();
  }, []);

  if (!report.groupBy) {
    return null;
  }

  const distributions = reportConfig.process.distribution;
  const selectedOption = distributions.find(({matcher}) => matcher(report));
  const hasDistribution = selectedOption.key !== 'none';

  const options = distributions
    .filter(
      ({visible, key}) =>
        visible(report) &&
        key !== 'none' &&
        (optimizeProfile === 'platform' ? true : !['assignee', 'candidateGroup'].includes(key))
    )
    .map(({key, enabled, label}) => {
      if (key === 'variable') {
        return (
          <Select.Submenu
            key="variable"
            label={label()}
            disabled={!enabled(report) || !variables || !variables?.length}
          >
            {variables?.map?.(({name}, idx) => {
              return (
                <Select.Option key={idx} value={key + '_' + name}>
                  {name}
                </Select.Option>
              );
            })}
          </Select.Submenu>
        );
      } else if (['startDate', 'endDate'].includes(key)) {
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
            createReportUpdate('process', report, 'distribution', type, {
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
          className="removeGrouping"
          onClick={() => onChange(createReportUpdate('process', report, 'distribution', 'none'))}
        >
          <Icon type="close-small" />
        </Button>
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
