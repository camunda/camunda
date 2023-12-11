/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button, ButtonSet} from '@carbon/react';

import {t} from 'translation';

interface AllColumnsButtonsProps {
  enableAll: () => void;
  disableAll: () => void;
}

export default function AllColumnsButtons({enableAll, disableAll}: AllColumnsButtonsProps) {
  return (
    <ButtonSet className="AllColumnsButtons">
      <Button size="sm" kind="primary" onClick={enableAll}>
        {t('common.enableAll')}
      </Button>
      <Button size="sm" kind="secondary" onClick={disableAll}>
        {t('common.disableAll')}
      </Button>
    </ButtonSet>
  );
}
