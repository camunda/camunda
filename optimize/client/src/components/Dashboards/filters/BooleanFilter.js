/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {Filter} from '@carbon/icons-react';
import {Button, Form, FormGroup, RadioButton, RadioButtonGroup} from '@carbon/react';
import classnames from 'classnames';

import {Popover} from 'components';
import {t} from 'translation';

export default function BooleanFilter({filter, setFilter}) {
  const value = filter?.values[0];

  let title = t('common.filter.list.operators.is') + ' ...';
  if (value === true) {
    title = t('common.filter.variableModal.bool.true');
  } else if (value === false) {
    title = t('common.filter.variableModal.bool.false');
  }

  return (
    <Popover
      isTabTip
      className="BooleanFilter"
      trigger={
        <Popover.ListBox size="sm">
          <Filter className={classnames('indicator', {active: filter})} />
          {title}
        </Popover.ListBox>
      }
    >
      <Form>
        <FormGroup legendText={t('common.value')} key={value}>
          <RadioButtonGroup name="bool-value">
            <RadioButton
              checked={value === true}
              value="true"
              labelText={t('common.filter.variableModal.bool.true')}
              onClick={() => setFilter({values: [true]})}
            />
            <RadioButton
              checked={value === false}
              value="false"
              labelText={t('common.filter.variableModal.bool.false')}
              onClick={() => setFilter({values: [false]})}
            />
          </RadioButtonGroup>
        </FormGroup>
        <hr />
        <Button
          size="sm"
          kind="ghost"
          className="reset-button"
          disabled={!filter}
          onClick={() => setFilter()}
        >
          {t('common.off')}
        </Button>
      </Form>
    </Popover>
  );
}
