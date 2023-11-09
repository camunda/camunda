/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import classnames from 'classnames';
import {useState, useEffect} from 'react';
import {Close} from '@carbon/icons-react';
import {Button} from '@carbon/react';

import {t} from 'translation';
import {reportConfig, createReportUpdate} from 'services';
import {CarbonSelect} from 'components';
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
          <CarbonSelect.Submenu
            key="variable"
            label={label()}
            disabled={!enabled(report) || !variables || !variables?.length}
            openToLeft
          >
            {variables?.map?.(({name, label}, idx) => {
              return (
                <CarbonSelect.Option key={idx} value={key + '_' + name} label={label || name} />
              );
            })}
          </CarbonSelect.Submenu>
        );
      } else if (['startDate', 'endDate'].includes(key)) {
        return (
          <CarbonSelect.Submenu key={key} label={label()} disabled={!enabled(report)} openToLeft>
            <CarbonSelect.Option value={key + '_automatic'} label={t('report.groupBy.automatic')} />
            <CarbonSelect.Option value={key + '_year'} label={t('report.groupBy.year')} />
            <CarbonSelect.Option value={key + '_month'} label={t('report.groupBy.month')} />
            <CarbonSelect.Option value={key + '_week'} label={t('report.groupBy.week')} />
            <CarbonSelect.Option value={key + '_day'} label={t('report.groupBy.day')} />
            <CarbonSelect.Option value={key + '_hour'} label={t('report.groupBy.hour')} />
          </CarbonSelect.Submenu>
        );
      }
      return (
        <CarbonSelect.Option key={key} value={key} disabled={!enabled(report)} label={label()} />
      );
    });

  if (!options.length) {
    return null;
  }

  return (
    <li className="DistributedBy GroupBy">
      <span className="label">{t('common.and')}</span>
      <CarbonSelect
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
      </CarbonSelect>
      {hasDistribution && (
        <Button
          size="sm"
          kind="ghost"
          iconDescription={t('common.reset')}
          className="removeGrouping"
          onClick={() => onChange(createReportUpdate('process', report, 'distribution', 'none'))}
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
