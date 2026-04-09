/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {searchVariables} from 'modules/api/v2/variables/searchVariables';
import {fetchDocument} from 'modules/api/v2/documents/fetchDocument';
import {parseAgentContext, mergeDocumentContent} from './parseAgentContext';
import type {AgentConversationModel} from './types';

/**
 * Fetches the `agentContext` variable scoped to a specific AHSP element
 * instance and parses it into a structured conversation model.
 *
 * When the conversation is stored as a Camunda document
 * (conversation.type === "camunda-document"), this hook automatically
 * fetches the document content via `GET /v2/documents/{documentId}`
 * and merges it into the model.
 */
function useAgentContext({
  processInstanceKey,
  elementInstanceKey,
  enabled = true,
}: {
  processInstanceKey: string;
  elementInstanceKey: string;
  enabled?: boolean;
}) {
  return useQuery<AgentConversationModel | null>({
    queryKey: ['agentContext', processInstanceKey, elementInstanceKey],
    queryFn: async () => {
      const {response, error} = await searchVariables({
        filter: {
          processInstanceKey: {$eq: processInstanceKey},
          scopeKey: {$eq: elementInstanceKey},
          name: {$eq: 'agentContext'},
        },
        page: {from: 0, limit: 1},
      });

      if (error !== null) {
        throw error;
      }

      const items = response?.items ?? [];
      if (items.length === 0) {
        return null;
      }

      const variable = items[0];
      let model = parseAgentContext(
        variable.value,
        variable.isTruncated ?? false,
      );

      // If conversation is stored as a document, try to fetch and merge it
      if (
        model.storageType === 'document_reference' &&
        model.conversationDocument
      ) {
        const doc = model.conversationDocument;
        const {response: docContent, error: docError} = await fetchDocument({
          documentId: doc.documentId,
          storeId: doc.storeId,
          contentHash: doc.contentHash,
        });

        if (docError !== null) {
          model = {
            ...model,
            documentResolved: true,
            warnings: [
              ...model.warnings,
              `Failed to fetch conversation document (documentId: ${doc.documentId}). ` +
                'The document may have expired (TTL) or the frontend may lack permissions. ' +
                'A backend endpoint with direct document-store access would handle this reliably.',
            ],
          };
        } else if (docContent !== null) {
          model = mergeDocumentContent(model, docContent);
        }
      }

      return model;
    },
    enabled,
    staleTime: 30_000,
  });
}

export {useAgentContext};
