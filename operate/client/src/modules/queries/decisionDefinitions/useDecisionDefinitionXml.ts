/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {fetchDecisionDefinitionXml} from 'modules/api/v2/decisionDefinitions/fetchDecisionDefinitionXml';
import {genericQueryOptions} from '../genericQuery';
import {type DecisionDefinition} from '@camunda/camunda-api-zod-schemas/8.8';

function useDecisionDefinitionXmlOptions({
  decisionDefinitionKey,
  enabled,
}: {
  decisionDefinitionKey: DecisionDefinition['decisionDefinitionKey'];
  enabled?: boolean;
}) {
  const queryKey = ['DecisionDefinitionXml', decisionDefinitionKey];

  return genericQueryOptions(
    queryKey,
    () => fetchDecisionDefinitionXml(decisionDefinitionKey),
    {
      queryKey,
      enabled,
      staleTime: 'static',
    },
  );
}

export {useDecisionDefinitionXmlOptions};
