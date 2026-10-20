/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {InlineLoading, InlineNotification} from '@carbon/react';
import {queryKeys} from 'modules/queries/queryKeys';
import {PreviewTextContent} from './styled';
import {MarkdownMessage} from '../../BottomPanelTabs/DetailsTab/AgentDetails/ConversationMessage/MarkdownMessage';

type PreviewTextProps = {
  src: string;
  fileName: string;
};

const PreviewText: React.FC<PreviewTextProps> = ({src, fileName}) => {
  const {data, isPending, isError} = useQuery({
    queryKey: queryKeys.documents.content(src),
    staleTime: 'static',
    queryFn: async ({signal}) => {
      // The link is already prefixed with the configured context path.
      const response = await fetch(src, {credentials: 'include', signal});

      if (!response.ok) {
        throw new Error(`Failed to load document (status ${response.status})`);
      }

      return response.text();
    },
  });

  if (isPending) {
    return <InlineLoading description="Preparing text preview…" />;
  }

  if (isError) {
    return (
      <InlineNotification
        kind="error"
        subtitle={`Failed to load text preview for "${fileName}".`}
        hideCloseButton
        lowContrast
      />
    );
  }

  if (/\.(md|markdown)$/i.test(fileName)) {
    return (
      <div aria-label={fileName}>
        <MarkdownMessage content={data} />
      </div>
    );
  }

  return <PreviewTextContent aria-label={fileName}>{data}</PreviewTextContent>;
};

export {PreviewText};
