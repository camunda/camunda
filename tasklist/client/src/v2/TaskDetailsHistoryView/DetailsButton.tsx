/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import {Information} from '@carbon/react/icons';
import {useTranslation} from 'react-i18next';

type Props = {
  onClick: () => void;
};

const DetailsButton: React.FC<Props> = ({onClick}) => {
  const {t} = useTranslation();

  return (
    <Button
      kind="ghost"
      size="sm"
      tooltipPosition="left"
      iconDescription={t('taskDetailsHistoryDetailsLabel')}
      aria-label={t('taskDetailsHistoryDetailsLabel')}
      onClick={onClick}
      hasIconOnly
      renderIcon={Information}
    />
  );
};

export {DetailsButton};
