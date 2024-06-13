/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
      label: string;
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

  return (
    <Container orientation="horizontal" gap={6}>
      {children}
    </Container>
  );
});

export {Header};
