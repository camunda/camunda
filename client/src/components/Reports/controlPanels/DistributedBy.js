/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import classnames from 'classnames';
import {useState, useEffect} from 'react';

import {t} from 'translation';
import {reportConfig, createReportUpdate, formatters} from 'services';
import {Select, Button, Icon, Input} from 'components';
import {getOptimizeProfile} from 'config';

export default function DistributedBy({report, onChange, variables}) {
  const [optimizeProfile, setOptimizeProfile] = useState();
  const [searchQuery, setSearchQuery] = useState('');

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
        const matchQuery = ({name, label}) =>
          (label || name).toLowerCase().includes(searchQuery.toLowerCase());

        return (
          <Select.Submenu
            key="variable"
            label={label()}
            disabled={!enabled(report) || !variables || !variables?.length}
            onClose={() => setSearchQuery('')}
          >
            <div className="searchContainer">
              <Icon className="searchIcon" type="search" />
              <Input
                type="text"
                className="searchInput"
                placeholder={t('report.groupBy.searchForVariable')}
                value={searchQuery}
                onChange={({target: {value}}) => setSearchQuery(value)}
                onClick={(evt) => evt.stopPropagation()}
                onKeyDown={(evt) => evt.stopPropagation()}
                // We progmatically trigger a click on the variable submenu on focus
                // This prevents closing it when moving the mouse outside it
                onFocus={(evt) => evt.target.closest('.Submenu:not(.fixed)')?.click()}
              />
            </div>
            {variables?.map?.(({name, label}, idx) => {
              return (
                <Select.Option
                  className={classnames({
                    hidden: !matchQuery({name, label}),
                  })}
                  key={idx}
                  value={key + '_' + name}
                  label={label || name}
                >
                  {formatters.getHighlightedText(label || name, searchQuery)}
                </Select.Option>
              );
            })}
            {variables?.filter(matchQuery).length === 0 && (
              <Select.Option disabled>{t('common.filter.variableModal.noVariables')}</Select.Option>
            )}
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
