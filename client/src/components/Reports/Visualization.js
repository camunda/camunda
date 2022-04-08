/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {reportConfig, createReportUpdate} from 'services';
import {Select} from 'components';

import './Visualization.scss';

export default function Visualization({type, report, onChange}) {
  const visualizations = reportConfig[type].visualization;
  const selectedOption = report.view ? visualizations.find(({matcher}) => matcher(report)) : null;

  return (
    <Select
      className="Visualization"
      onChange={(value) => {
        onChange(createReportUpdate(type, report, 'visualization', value));
      }}
      value={selectedOption?.key}
      disabled={!selectedOption}
    >
      {selectedOption &&
        visualizations
          .filter(({visible}) => visible(report))
          .map(({key, enabled, label}) => (
            <Select.Option key={key} value={key} disabled={!enabled(report)}>
              {label()}
            </Select.Option>
          ))}
    </Select>
  );
}
