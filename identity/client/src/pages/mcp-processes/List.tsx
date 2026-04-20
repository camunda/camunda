/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Loading } from "@carbon/react";
import useTranslate from "src/utility/localization";
import Page, { PageHeader } from "src/components/layout/Page";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import { useMcpProcessTools } from "./useMcpProcessTools";

const List: FC = () => {
  const { t } = useTranslate();

  const { processTools, loading, success, reload } = useMcpProcessTools();

  return (
    <Page>
      <PageHeader
        title={t("mcpProcesses")}
        linkText={t("mcpProcesses").toLowerCase()}
        shouldShowDocumentationLink={false}
      />
      {loading && <Loading withOverlay={false} />}
      {!loading && success && (
        <pre>{JSON.stringify(processTools, null, 2)}</pre>
      )}
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("errorOccurred")}
          actionButton={{ label: t("retry"), onClick: reload }}
        />
      )}
    </Page>
  );
};

export default List;
