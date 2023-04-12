/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactNode} from 'react';
import {withDocs, WithDocsProps} from 'HOC';

interface DocsLinkProps extends WithDocsProps {
  location: string;
  children: ReactNode;
}

const DocsLink = ({docsLink, location, children}: DocsLinkProps): JSX.Element => {
  return (
    <a href={docsLink + location} target="_blank" rel="noopener noreferrer">
      {children}
    </a>
  );
};

export default withDocs(DocsLink);
