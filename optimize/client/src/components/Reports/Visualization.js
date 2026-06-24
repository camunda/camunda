/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {reportConfig, createReportUpdate} from 'services';
import {Select} from 'components';
import {t} from 'translation';

import './Visualization.scss';

export default function Visualization({report, onChange}) {
  const visualizations = reportConfig.visualization;
  const selectedOption = report.view ? visualizations.find(({matcher}) => matcher(report)) : null;

  return (
    <Select
      labelText={t(`report.visualization.label`).toString()}
      className="Visualization"
      onChange={(value) => {
        onChange(createReportUpdate(report, 'visualization', value));
      }}
      value={selectedOption?.key}
      disabled={!selectedOption}
      size="md"
    >
      {selectedOption &&
        visualizations
          .filter(({visible}) => visible(report))
          .map(({key, enabled, label}) => (
            <Select.Option key={key} value={key} label={label()} disabled={!enabled(report)} />
          ))}
    </Select>
  );
}
