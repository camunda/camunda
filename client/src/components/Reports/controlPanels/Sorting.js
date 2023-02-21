/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Select} from 'components';
import {createReportUpdate, getDefaultSorting, reportConfig} from 'services';
import {t} from 'translation';

export default function Sorting({type: reportType, report, onChange}) {
  if (!report.view) {
    return null;
  }

  const sortingOrder = reportConfig[reportType].sortingOrder;
  const options = sortingOrder.filter(({visible}) => visible(report));

  if (!options.length) {
    return null;
  }

  const defaultSorting = getDefaultSorting({reportType, data: report});

  const {order} = report.configuration.sorting || defaultSorting;

  const onSortingChange = (type) => (value) =>
    onChange(createReportUpdate(reportType, report, type, value));

  return (
    <li className="sortingOrder">
      <span className="label">{t('report.sorting.label')}</span>
      <Select onChange={onSortingChange('sortingOrder')} value={order}>
        {options.map(({key, label}) => (
          <Select.Option key={key} value={key}>
            {label()}
          </Select.Option>
        ))}
      </Select>
    </li>
  );
}
