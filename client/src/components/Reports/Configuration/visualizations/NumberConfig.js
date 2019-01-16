import React from 'react';

import {Switch, Input} from 'components';

import CountTargetInput from './subComponents/CountTargetInput';
import DurationTargetInput from './subComponents/DurationTargetInput';

import './NumberConfig.scss';

export default function NumberConfig({report, configuration, onChange}) {
  const targetValue = configuration.targetValue;

  const precisionSet = typeof configuration.precision === 'number';
  const countOperation = report.data.view.operation === 'count';
  const goalSet = targetValue.active;

  return (
    <div className="NumberConfig">
      <fieldset>
        <legend>
          <Switch
            checked={precisionSet}
            onChange={evt => onChange('precision', evt.target.checked ? 1 : null)}
          />
          Limit Precision
        </legend>
        <Input
          className="precision"
          disabled={typeof configuration.precision !== 'number'}
          onChange={() => {}}
          onKeyDown={evt => {
            const number = parseInt(evt.key, 10);
            if (number) {
              onChange('precision', number);
            }
          }}
          value={precisionSet ? configuration.precision : 1}
        />
        most significant
        {countOperation ? ' digits' : ' units'}
      </fieldset>
      <fieldset>
        <legend>
          <Switch
            checked={goalSet}
            onChange={evt =>
              onChange('targetValue', {
                ...targetValue,
                active: evt.target.checked
              })
            }
          />Goal
        </legend>
        {countOperation ? (
          <CountTargetInput
            baseline={targetValue.countProgress.baseline}
            target={targetValue.countProgress.target}
            disabled={!goalSet}
            onChange={(type, value) =>
              onChange('targetValue', {
                ...targetValue,
                countProgress: {...targetValue.countProgress, [type]: value}
              })
            }
          />
        ) : (
          <DurationTargetInput
            baseline={targetValue.durationProgress.baseline}
            target={targetValue.durationProgress.target}
            disabled={!goalSet}
            onChange={(type, subType, value) =>
              onChange('targetValue', {
                ...targetValue,
                durationProgress: {
                  ...targetValue.durationProgress,
                  [type]: {
                    ...targetValue.durationProgress[type],
                    [subType]: value
                  }
                }
              })
            }
          />
        )}
      </fieldset>
    </div>
  );
}
