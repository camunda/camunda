/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {C3EmptyState} from '@camunda/camunda-composite-components';
import {Container, PoweredBy} from './styled';

type Props = React.ComponentProps<typeof C3EmptyState>;

const Message: React.FC<Props> = (props) => {
  return (
    <Container>
      <C3EmptyState {...props} />
      <PoweredBy />
    </Container>
  );
};

export {Message};
