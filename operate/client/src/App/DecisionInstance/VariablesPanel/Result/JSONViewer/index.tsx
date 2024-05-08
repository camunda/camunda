/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
      <JSONEditor value={value} readOnly height="100%" />
    </Container>
  );
});

export {JSONViewer};
