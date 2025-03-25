/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  fetchDecisionDefinitionXml,
  DecisionDefinitionKey,
} from 'modules/api/v2/decisionDefinitions/fetchDecisionDefinitionXml';
import {genericQueryOptions} from '../genericQuery';

function getQueryKey(DecisionDefinitionKey: DecisionDefinitionKey) {
  return ['DecisionDefinitionXml', DecisionDefinitionKey];
}

function useDecisionDefinitionXmlOptions({
  decisionDefinitionKey,
  enabled,
}: {
  decisionDefinitionKey: DecisionDefinitionKey;
  enabled?: boolean;
}) {
  const queryKey = getQueryKey(decisionDefinitionKey);

  return genericQueryOptions(
    queryKey,
    () => fetchDecisionDefinitionXml(decisionDefinitionKey),
    {
      queryKey,
      enabled,
    },
  );
}

export {useDecisionDefinitionXmlOptions};
