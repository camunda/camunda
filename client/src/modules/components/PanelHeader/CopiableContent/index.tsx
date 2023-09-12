/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
