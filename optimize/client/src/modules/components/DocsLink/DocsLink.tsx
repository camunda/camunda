/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactNode} from 'react';
import {Link} from '@carbon/react';

import {useDocs} from 'hooks';

interface DocsLinkProps {
  location: string;
  children: ReactNode;
}

export default function DocsLink({location, children}: DocsLinkProps): JSX.Element {
  const {generateDocsLink} = useDocs();

  return (
    <Link href={generateDocsLink(location)} target="_blank" rel="noopener noreferrer">
      {children}
    </Link>
  );
}
