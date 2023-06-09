/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classnames from 'classnames';

import {CarbonPopover, Form, Button, ButtonGroup, Icon} from 'components';
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
    <CarbonPopover
      className="BooleanFilter"
      title={
        <>
          <Icon type="filter" className={classnames('indicator', {active: filter})} /> {title}
        </>
      }
    >
      <Form compact>
        <fieldset>
          <ButtonGroup>
            <Button active={value === true} onClick={() => setFilter({values: [true]})}>
              {t('common.filter.variableModal.bool.true')}
            </Button>
            <Button active={value === false} onClick={() => setFilter({values: [false]})}>
              {t('common.filter.variableModal.bool.false')}
            </Button>
          </ButtonGroup>
        </fieldset>
        <hr />
        <Button className="reset-button" disabled={!filter} onClick={() => setFilter()}>
          {t('common.off')}
        </Button>
      </Form>
    </CarbonPopover>
  );
}
