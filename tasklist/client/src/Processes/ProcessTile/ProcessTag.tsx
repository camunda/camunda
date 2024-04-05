/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
