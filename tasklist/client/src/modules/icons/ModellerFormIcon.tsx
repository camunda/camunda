/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import FormIcon from 'modules/icons/modeller-form-icon.svg?react';

const ModellerFormIcon: React.FC<
  Omit<React.ComponentProps<typeof FormIcon>, 'focusable' | 'aria-hidden'>
> = (props) => {
  return <FormIcon {...props} focusable={false} aria-hidden />;
};

export {ModellerFormIcon};
