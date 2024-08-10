/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CodeSnippetContainer} from './styled';
import {CodeSnippet} from '@carbon/react';

type Props = {
  content: string;
  copyButtonDescription: string;
};

const CopiableContent: React.FC<Props> = ({content, copyButtonDescription}) => {
  return (
    <CodeSnippetContainer>
      <CodeSnippet
        type="inline"
        aria-label={copyButtonDescription}
        feedback="Copied to clipboard"
      >
        {content}
      </CodeSnippet>
    </CodeSnippetContainer>
  );
};

export {CopiableContent};
