/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Switch, Form} from 'components';
import {t} from 'translation';

export default function UndefinedOptions({
  includeUndefined,
  changeIncludeUndefined,
  excludeUndefined,
  changeExcludeUndefined,
}) {
  return (
    <>
      <Form.Group>
        <Switch
          checked={includeUndefined}
          onChange={({target: {checked}}) => changeIncludeUndefined(checked)}
          label={
            <span
              dangerouslySetInnerHTML={{
                __html: t(`common.filter.variableModal.includeUndefined`),
              }}
            />
          }
        />
      </Form.Group>
      <Form.Group noSpacing>
        <Switch
          checked={excludeUndefined}
          onChange={({target: {checked}}) => changeExcludeUndefined(checked)}
          label={
            <span
              dangerouslySetInnerHTML={{
                __html: t(`common.filter.variableModal.excludeUndefined`),
              }}
            />
          }
        />
      </Form.Group>
    </>
  );
}
