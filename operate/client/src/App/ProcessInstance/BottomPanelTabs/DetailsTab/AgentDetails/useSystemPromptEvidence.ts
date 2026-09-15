/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {z} from 'zod';
import {getVariable} from 'modules/api/v2/variables/getVariable';
import {searchVariables} from 'modules/api/v2/variables/searchVariables';
import {queryKeys} from 'modules/queries/queryKeys';

const systemPromptEvidenceSchema = z.object({
  systemPrompt: z.object({
    type: z.literal('linked'),
    promptId: z.string().min(1),
    binding: z.literal('latest'),
    version: z.number().int().positive(),
    prompt: z.string().min(1),
  }),
});

const parseSystemPromptEvidence = (value: string) => {
  const result = systemPromptEvidenceSchema.safeParse(JSON.parse(value));

  if (!result.success) {
    throw new Error('The persisted agent system prompt evidence is invalid.');
  }

  const {type, binding, ...evidence} = result.data.systemPrompt;

  return {
    ...evidence,
    type: type === 'linked' ? 'Linked' : type,
    binding: binding === 'latest' ? 'Latest' : binding,
  };
};

const useSystemPromptEvidence = ({
  processInstanceKey,
  enabled,
}: {
  processInstanceKey: string;
  enabled: boolean;
}) =>
  useQuery({
    queryKey: queryKeys.variables.agentSystemPromptEvidence(processInstanceKey),
    queryFn: async () => {
      const {response, error} = await searchVariables({
        filter: {
          processInstanceKey: {$eq: processInstanceKey},
          scopeKey: {$eq: processInstanceKey},
          name: {$eq: 'agent'},
        },
        page: {limit: 1},
      });

      if (response === null) {
        throw error;
      }

      const variable = response.items[0];

      if (variable === undefined) {
        return null;
      }

      if (!variable.isTruncated) {
        return parseSystemPromptEvidence(variable.value);
      }

      const {response: fullVariable, error: fullVariableError} =
        await getVariable(variable.variableKey);

      if (fullVariable === null) {
        throw fullVariableError;
      }

      return parseSystemPromptEvidence(fullVariable.value);
    },
    enabled: enabled && processInstanceKey !== '',
  });

export {parseSystemPromptEvidence, useSystemPromptEvidence};
