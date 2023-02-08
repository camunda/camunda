/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createPortal} from 'react-dom';
import {Modal as BaseModal} from '@carbon/react';
import {CarbonTheme} from 'modules/theme/CarbonTheme';

type Props = React.ComponentProps<typeof BaseModal>;

const Modal: React.FC<Props> = ({placeholder, children, ...props}) => {
  return createPortal(
    <CarbonTheme>
      <BaseModal {...props}>{children}</BaseModal>
    </CarbonTheme>,
    document.body,
  );
};

export {Modal};
