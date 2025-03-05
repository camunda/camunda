/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import FormIcon from 'common/images//modeller-form-icon.svg?react';

const ModellerFormIcon: React.FC<
  Omit<React.ComponentProps<typeof FormIcon>, 'focusable' | 'aria-hidden'>
> = (props) => {
  return <FormIcon {...props} focusable={false} aria-hidden />;
};

export {ModellerFormIcon};
