/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import '@camunda/design-system/styles.css';
import {C4Provider, Text} from '@camunda/design-system';
import {observer} from 'mobx-react';
import {currentTheme} from 'modules/stores/currentTheme';
import {Reasoning, Root} from './styled';

type ReasoningNoteProps = {
  reasoning: string;
};

const ReasoningNote = observer(({reasoning}: ReasoningNoteProps) => (
  <C4Provider theme={currentTheme.theme}>
    <Root aria-label="Thinking">
      <Text
        as="div"
        variant="label-sm"
        className="font-medium text-neutral-foreground-subtle"
      >
        Thinking
      </Text>
      <Reasoning as="p" variant="helper">
        {reasoning}
      </Reasoning>
    </Root>
  </C4Provider>
));

export {ReasoningNote};
