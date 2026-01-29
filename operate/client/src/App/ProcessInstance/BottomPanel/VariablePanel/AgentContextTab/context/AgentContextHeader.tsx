/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Accordion,
  AccordionItem,
  Grid,
  Column,
  Tag,
  UnorderedList,
  ListItem,
  CodeSnippet,
} from '@carbon/react';
import type {AgentContext} from 'modules/agentContext/types';
import {SectionTitle} from '../timeline/styled';
import {HeaderCard} from './styled';

type Props = {
  agentContext: AgentContext;
};

function getSystemPrompt(ctx: AgentContext): string | null {
  const system = ctx.conversation?.messages.find((m) => m.role === 'system');
  const parts = system?.content ?? [];
  const text = parts
    .map((p) => p.text ?? p.content ?? '')
    .filter((t) => t.trim() !== '')
    .join('\n');
  return text.trim() === '' ? null : text;
}

const AgentContextHeader: React.FC<Props> = ({agentContext}) => {
  const systemPrompt = getSystemPrompt(agentContext);

  return (
    <>
      <SectionTitle>Agent</SectionTitle>
      <Accordion align="start">
        {(agentContext.toolDefinitions?.length ?? 0) > 0 && (
          <AccordionItem title="Tool definitions">
            <UnorderedList>
              {(agentContext.toolDefinitions ?? []).map((t) => (
                <ListItem key={t.name}>
                  <strong>{t.name}</strong>
                  {t.description ? ` — ${t.description}` : ''}
                </ListItem>
              ))}
            </UnorderedList>
          </AccordionItem>
        )}

        {systemPrompt && (
          <AccordionItem title="System prompt">
            <CodeSnippet type="multi" feedback="Copied to clipboard">
              {systemPrompt}
            </CodeSnippet>
          </AccordionItem>
        )}
      </Accordion>
      <br></br>
    </>
  );
};

export {AgentContextHeader};
