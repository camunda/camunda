/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FormGroup, Toggle} from '@carbon/react';

import {t} from 'translation';

interface ShowInstanceCountProps {
  showInstanceCount: boolean;
  label: string;
  onChange: (change: {showInstanceCount: {$set: boolean}}) => void;
}

export default function ShowInstanceCount({
  showInstanceCount,
  onChange,
  label,
}: ShowInstanceCountProps) {
  return (
    <FormGroup legendText={t(`report.config.showCount.${label}`)} className="ShowInstanceCount">
      <Toggle
        id="showInstanceCount"
        size="sm"
        toggled={!!showInstanceCount}
        onToggle={(checked) => onChange({showInstanceCount: {$set: checked}})}
        labelA={t('common.off').toString()}
        labelB={t('common.on').toString()}
      />
    </FormGroup>
  );
}
