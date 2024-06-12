/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tag} from '@carbon/react';
import {ModellerFormIcon} from 'modules/icons/ModellerFormIcon';

type Props = {
  variant: 'start-form';
};

const ProcessTag: React.FC<Props> = ({variant}) => {
  if (variant === 'start-form') {
    return <Tag renderIcon={ModellerFormIcon}>Requires form input</Tag>;
  } else {
    return null;
  }
};

export {ProcessTag};
