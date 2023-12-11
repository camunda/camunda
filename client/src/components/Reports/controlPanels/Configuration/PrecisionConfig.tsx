/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Information} from '@carbon/icons-react';
import {FormGroup, TextInput, Toggle, Tooltip} from '@carbon/react';

import {t} from 'translation';

import './PrecisionConfig.scss';

interface PrecisionConfigProps {
  configuration: {precision: number | null};
  view: {properties: string[]; entity?: string};
  type?: string;
  onChange: (change: {precision: {$set: number | null}}) => void;
}

export default function PrecisionConfig({
  configuration,
  onChange,
  view,
  type,
}: PrecisionConfigProps) {
  const precisionSet = typeof configuration.precision === 'number';
  const countOperation =
    view &&
    (view.properties.includes('frequency') ||
      view.properties.includes('percentage') ||
      view.entity === 'variable');

  return (
    <FormGroup
      className="PrecisionConfig"
      legendText={
        <>
          <Toggle
            id="precissionToggle"
            size="sm"
            toggled={precisionSet}
            onToggle={(checked) => onChange({precision: {$set: checked ? 1 : null}})}
            labelA={t('report.config.limitPrecision.legend').toString()}
            labelB={t('report.config.limitPrecision.legend').toString()}
          />
          {type !== 'number' && type !== 'table' && (
            <Tooltip label={t('report.config.limitPrecision.tooltip')}>
              <button type="button">
                <Information />
              </button>
            </Tooltip>
          )}
        </>
      }
    >
      <TextInput
        id="precission"
        className="precision"
        labelText={t(
          `report.config.limitPrecision.numberOf.${countOperation ? 'digits' : 'units'}`
        )}
        disabled={typeof configuration.precision !== 'number'}
        onKeyDown={(evt) => {
          const number = parseInt(evt.key, 10);
          if (number) {
            onChange({precision: {$set: number}});
          }
        }}
        value={precisionSet ? (configuration.precision as number) : 1}
      />
    </FormGroup>
  );
}
