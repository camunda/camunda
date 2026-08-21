/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, ReactNode } from "react";
import useTranslate from "../../utility/localization";
import { useDocsUrl } from "../documentation/DocsUrlContext";
import { ExternalLink } from "lucide-react";

type DocumentationLinkProps = {
  children?: ReactNode;
  path?: string;
  withIcon?: boolean;
};

export const documentationHref = (
  docsUrl: string,
  path: DocumentationLinkProps["path"],
) => `${docsUrl}${path}`;

export const DocumentationLink: FC<DocumentationLinkProps> = ({
  path = "",
  withIcon = false,
  children,
}) => {
  const { Translate } = useTranslate();
  const docsUrl = useDocsUrl();

  return (
    <a
      href={documentationHref(docsUrl, path)}
      data-test="documentation-link"
      target="_blank"
      rel="noreferrer noopener"
      className="rounded-sm text-info-action-default underline-offset-2 hover:underline focus-visible:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      {children || <Translate>documentation</Translate>}
      {withIcon && (
        <ExternalLink
          aria-hidden="true"
          className="ms-1 inline size-4 align-text-bottom"
        />
      )}
    </a>
  );
};
