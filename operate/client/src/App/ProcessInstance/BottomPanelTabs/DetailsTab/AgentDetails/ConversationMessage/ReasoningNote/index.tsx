/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {C4Provider, Text} from '@camunda/design-system';
import {observer} from 'mobx-react';
import {useId} from 'react';
import {currentTheme} from 'modules/stores/currentTheme';
import {Root} from './styled';

type ReasoningNoteProps = {
  reasoning: string;
};

const ReasoningNote = observer(({reasoning}: ReasoningNoteProps) => {
  const labelId = useId();

  return (
    <C4Provider theme={currentTheme.theme}>
      <Root aria-labelledby={labelId}>
        <Text
          id={labelId}
          as="span"
          variant="label-sm"
          className="font-medium text-neutral-foreground-subtle"
        >
          Thinking
        </Text>
        <Text
          as="p"
          variant="body-subtle"
          className="m-0 break-words whitespace-pre-wrap italic text-neutral-foreground-subtle"
        >
          {reasoning}
        </Text>
      </Root>
    </C4Provider>
  );
});

export {ReasoningNote};
