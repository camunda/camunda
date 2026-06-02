/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense} from 'react';
import {useQuery} from '@tanstack/react-query';
import {InlineLoading, InlineNotification} from '@carbon/react';
import {beautifyJSON} from 'modules/utils/editor/beautifyJSON';
import type {DocumentInfo} from '../DocumentValueCell/parseDocumentVariable';
import {PreviewJSONContainer} from './styled';
import {queryKeys} from 'modules/queries/queryKeys';

const RichTextEditor = lazy(async () => {
  const [{loadMonaco}, {RichTextEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/RichTextEditor'),
  ]);
  loadMonaco();
  return {default: RichTextEditor};
});

type PreviewJsonProps = {
  document: DocumentInfo;
};

const PreviewJson: React.FC<PreviewJsonProps> = ({document}) => {
  const {data, isPending, isError} = useQuery({
    queryKey: queryKeys.documents.content(document.link),
    staleTime: 'static',
    queryFn: async ({signal}) => {
      // Note: Cannot use one of the request helpers here, because the link is
      // already prefixed with a configured context-path.
      const response = await fetch(document.link, {
        credentials: 'include',
        headers: {Accept: 'application/json'},
        signal,
      });

      if (!response.ok) {
        throw new Error(`Failed to load document (status ${response.status})`);
      }

      return beautifyJSON(await response.text());
    },
  });

  return (
    <PreviewJSONContainer>
      {isPending && (
        <InlineLoading
          data-testid="json-document-preview-spinner"
          description="Preparing JSON preview…"
        />
      )}
      {isError && (
        <InlineNotification
          kind="error"
          subtitle={`Failed to load JSON preview for "${document.fileName}".`}
          hideCloseButton
          lowContrast
        />
      )}
      {data !== undefined && (
        <Suspense
          fallback={
            <InlineLoading
              data-testid="json-document-preview-spinner"
              description="Preparing JSON preview…"
            />
          }
        >
          <RichTextEditor value={data} language="json" readOnly height="80vh" />
        </Suspense>
      )}
    </PreviewJSONContainer>
  );
};

export {PreviewJson};
