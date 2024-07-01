/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Report, SingleReportConfiguration} from 'types';
import {Spec} from 'immutability-helper';

import CountTargetInput from './CountTargetInput';
import DurationTargetInput from './DurationTargetInput';

interface TargetSelectionProps {
  report: Report<'process'> | Report<'decision'>;
  onChange: (change: Spec<SingleReportConfiguration>) => void;
  hideBaseLine?: boolean;
}

export default function TargetSelection({report, onChange, hideBaseLine}: TargetSelectionProps) {
  const {configuration, view} = report.data;
  const targetValue = configuration.targetValue;
  const isPercentageReport = view?.properties.includes('percentage');
  const countOperation =
    view?.properties.includes('frequency') ||
    isPercentageReport ||
    (view && 'entity' in view && view.entity === 'variable');

  if (countOperation) {
    return (
      <CountTargetInput
        baseline={targetValue.countProgress.baseline}
        target={targetValue.countProgress.target}
        isBelow={targetValue.countProgress.isBelow}
        disabled={!targetValue.active}
        isPercentageReport={isPercentageReport}
        hideBaseLine={hideBaseLine}
        onChange={(type, value) =>
          onChange({targetValue: {countProgress: {[type]: {$set: value}}}})
        }
      />
    );
  } else {
    return (
      <DurationTargetInput
        baseline={targetValue.durationProgress.baseline}
        target={targetValue.durationProgress.target}
        disabled={!targetValue.active}
        hideBaseLine={hideBaseLine}
        onChange={(type, subType, value) =>
          onChange({targetValue: {durationProgress: {[type]: {[subType]: {$set: value}}}}})
        }
      />
    );
  }
}
