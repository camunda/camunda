/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {Container, Label} from './styled';
import {Stack} from '@carbon/react';

type Props =
  | {
      mode: 'view';
      label: string;
      processName: string;
      processVersion: string;
    }
  | {
      mode: 'edit';
      children: React.ReactNode;
    };

const Header: React.FC<Props> = observer((props) => {
  if (props.mode === 'view') {
    const {label, processName, processVersion} = props;

    return (
      <Container orientation="horizontal" gap={6}>
        <Stack orientation="horizontal" gap={5}>
          <Label>{label}</Label>
          <span>{processName}</span>
        </Stack>
        <Stack orientation="horizontal" gap={5}>
          <Label>Version</Label>
          <span>{processVersion}</span>
        </Stack>
      </Container>
    );
  }

  const {children} = props;

  return <Container orientation="horizontal">{children}</Container>;
});

export {Header};
