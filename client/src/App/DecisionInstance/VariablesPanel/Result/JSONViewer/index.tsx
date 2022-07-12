/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react-lite';
import {JSONEditor} from 'modules/components/JSONEditor';
import {Container} from './styled';

type Props = {
  value: string;
  'data-testid'?: string;
};

const JSONViewer: React.FC<Props> = observer(({value, ...props}) => {
  return (
    <Container data-testid={props['data-testid']}>
      <JSONEditor value={value} readOnly />
    </Container>
  );
});

export {JSONViewer};
