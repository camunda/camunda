/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
    <Link
      className="cds--link"
      href={generateDocsLink(location)}
      target="_blank"
      rel="noopener noreferrer"
    >
      {children}
    </Link>
  );
}
