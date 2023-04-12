/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentProps} from 'react';

declare module '@carbon/react' {
  declare function Button(
    props: {
      kind?: 'danger' | 'ghost' | 'primary' | 'secondary' | 'tertiary';
    } & ComponentProps<'button'>
  );

  declare function ComposedModal(
    props: {
      open?: boolean;
      onClose?: () => void;
      size?: 'xs' | 'sm' | 'md' | 'lg';
      isFullWidth?: boolean;
    } & ComponentProps<'div'>
  );

  declare function ModalBody(props: ComponentProps<'div'>);
  declare function ModalHeader(props: ComponentProps<'div'>);
  declare function ModalFooter(props: ComponentProps<'div'>);
}
