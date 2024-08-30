/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {FormGroup, Toggle} from '@carbon/react';

import {t} from 'translation';

interface ShowInstanceCountProps {
  showInstanceCount: boolean;
  onChange: (change: {showInstanceCount: {$set: boolean}}) => void;
}

export default function ShowInstanceCount({showInstanceCount, onChange}: ShowInstanceCountProps) {
  return (
    <FormGroup legendText={t(`report.config.showCount.instance`)} className="ShowInstanceCount">
      <Toggle
        id="showInstanceCount"
        size="sm"
        toggled={!!showInstanceCount}
        onToggle={(checked) => onChange({showInstanceCount: {$set: checked}})}
        labelText={t('common.off').toString()}
        hideLabel
      />
    </FormGroup>
  );
}
