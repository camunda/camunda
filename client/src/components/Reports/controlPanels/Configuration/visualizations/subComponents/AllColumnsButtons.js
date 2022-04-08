/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {ButtonGroup, Button} from 'components';
import './AllColumnsButtons.scss';
import {t} from 'translation';

export default function AllColumnsButtons({enableAll, disableAll}) {
  return (
    <ButtonGroup className="AllColumnsButtons">
      <Button onClick={enableAll}>{t('common.enableAll')}</Button>
      <Button onClick={disableAll}>{t('common.disableAll')}</Button>
    </ButtonGroup>
  );
}
