/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Link} from '@carbon/react';
import {Container} from './styled';

type Props = {
  className?: string;
};

const Disclaimer: React.FC<Props> = (props) => {
  return window.clientConfig?.isEnterprise ? null : (
    <Container {...props}>
      Non-Production License. If you would like information on production usage,
      please refer to our{' '}
      <Link
        href="https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-self-managed/"
        target="_blank"
        inline
      >
        terms & conditions page
      </Link>{' '}
      or{' '}
      <Link href="https://camunda.com/contact/" target="_blank" inline>
        contact sales
      </Link>
      .
    </Container>
  );
};

export {Disclaimer};
