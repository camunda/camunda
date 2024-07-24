/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tag} from '@carbon/react';
import {ModellerFormIcon} from 'modules/icons/ModellerFormIcon';
import {useTranslation} from 'react-i18next';

type Props = {
  variant: 'start-form';
};

const ProcessTag: React.FC<Props> = ({variant}) => {
  
  const {t} = useTranslation();

  if (variant === 'start-form') {
    return <Tag renderIcon={ModellerFormIcon}>{t('requiresFormInput')}</Tag>;
  } else {
    return null;
  }
};

export {ProcessTag};
