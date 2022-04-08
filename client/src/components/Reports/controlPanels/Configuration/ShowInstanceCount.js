/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Form, Switch} from 'components';
import {t} from 'translation';

export default function ShowInstanceCount({configuration, onChange, label}) {
  return (
    <Form.Group noSpacing>
      <Switch
        checked={!!configuration.showInstanceCount}
        onChange={({target: {checked}}) => onChange({showInstanceCount: {$set: checked}})}
        label={t(`report.config.showCount.${label}`)}
      />
    </Form.Group>
  );
}
