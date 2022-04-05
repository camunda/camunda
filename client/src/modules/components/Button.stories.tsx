/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import React from 'react';
import {Button} from './Button';

export default {
  title: 'Components/Modules/Button',
  component: Button,
};

const Primary: React.FC = () => {
  return <Button>I am a button</Button>;
};

const PrimaryDisabled: React.FC = () => {
  return <Button disabled>I am a disabled button</Button>;
};

const Small: React.FC = () => {
  return <Button variant="small">I am a small button</Button>;
};

const SmallDisabled: React.FC = () => {
  return (
    <Button variant="small" disabled>
      I am a small disabled button
    </Button>
  );
};

export {Primary, PrimaryDisabled, Small, SmallDisabled};
