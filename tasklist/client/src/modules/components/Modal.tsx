/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createPortal} from 'react-dom';
import {Modal as BaseModal} from '@carbon/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

type Props = React.ComponentProps<typeof BaseModal>;

const Modal: React.FC<Props> = ({children, ...props}) => {
  return createPortal(
    <ThemeProvider>
      <BaseModal {...props}>{children}</BaseModal>
    </ThemeProvider>,
    document.body,
  );
};

export {Modal};
