/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createPortal} from 'react-dom';
import {Modal as BaseModal, ComposedModal} from '@carbon/react';
import {ThemeProvider} from 'common/theme/ThemeProvider';

type Props =
  | (React.ComponentProps<typeof BaseModal> & {
      variant?: 'modal';
    })
  | (React.ComponentProps<typeof ComposedModal> & {
      variant?: 'composed-modal';
    });

const Modal: React.FC<Props> = ({
  children,
  variant = 'modal',
  launcherButtonRef,
  ...props
}) => {
  if (variant === 'composed-modal') {
    return createPortal(
      <ThemeProvider>
        <ComposedModal {...props}>{children}</ComposedModal>
      </ThemeProvider>,
      document.body,
    );
  }

  return createPortal(
    <ThemeProvider>
      <BaseModal launcherButtonRef={launcherButtonRef} {...props}>
        {children}
      </BaseModal>
    </ThemeProvider>,
    document.body,
  );
};

export {Modal};
