/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { Button, EmptyState } from "@camunda/design-system";
import { Plus } from "lucide-react";
import { FC } from "react";
import { documentationHref } from "src/components/documentationV2";
import { useDocsUrl } from "../documentation/DocsUrlContext";
import useTranslate from "src/utility/localization";

type PageEmptyStateProps = {
  resourceTypeTranslationKey: string;
  docsLinkPath?: string;
  handleClick: () => void;
};

const PageEmptyState: FC<PageEmptyStateProps> = ({
  resourceTypeTranslationKey,
  docsLinkPath = "",
  handleClick,
}) => {
  const { t } = useTranslate();
  const docsUrl = useDocsUrl();

  const resourceTypeText = t(resourceTypeTranslationKey).toLowerCase();

  return (
    <EmptyState
      heading={t("emptyStateTitleCreate", {
        resourceType: resourceTypeText,
      })}
      description={t("emptyStateSubtitleCreate", {
        resourceType: resourceTypeText,
      })}
      action={
        <Button onClick={handleClick}>
          <Plus data-icon="inline-start" aria-hidden="true" />
          {t("emptyStateButtonCreate", { resourceType: resourceTypeText })}
        </Button>
      }
      secondaryAction={
        <Button variant="link" size="sm" asChild>
          <a
            href={documentationHref(docsUrl, docsLinkPath)}
            target="_blank"
            rel="noreferrer noopener"
          >
            {t("emptyStateLearnText", { resourceType: resourceTypeText })}
          </a>
        </Button>
      }
    />
  );
};

export default PageEmptyState;
