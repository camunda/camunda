/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Select} from 'components';
import {createReportUpdate, getDefaultSorting, reportConfig} from 'services';
import {t} from 'translation';

export default function Sorting({report, onChange}) {
  if (!report.view) {
    return null;
  }

  const sortingOrder = reportConfig.sortingOrder;
  const options = sortingOrder.filter(({visible}) => visible(report));

  if (!options.length) {
    return null;
  }

  const defaultSorting = getDefaultSorting({data: report});

  const {order} = report.configuration.sorting || defaultSorting;

  const onSortingChange = (type) => (value) => onChange(createReportUpdate(report, type, value));

  return (
    <li className="sortingOrder">
      <span className="label">{t('report.sorting.label')}</span>
      <Select onChange={onSortingChange('sortingOrder')} value={order}>
        {options.map(({key, label}) => (
          <Select.Option key={key} value={key} label={label()} />
        ))}
      </Select>
    </li>
  );
}
