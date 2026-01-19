/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import useTranslate from "src/utility/localization";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import { useApi } from "src/utility/api";
import { searchAuditLogs } from "src/utility/api/audit-logs";

const List: FC = () => {
  const { t } = useTranslate("operationsLog");
  const { t: tComponents } = useTranslate();
  const {
    data: auditLogs,
    loading,
    success,
    reload,
  } = useApi(searchAuditLogs, {
    sort: [
      {
        field: "timestamp",
        order: "desc",
      },
    ],
    filter: {
      category: {
        $eq: "ADMIN",
      },
    },
    page: {
      from: 0,
      limit: 50,
    },
  });

  return (
    <Page>
      <PageHeader
        title={t("operationsLog")}
        linkText={t("operationsLog").toLowerCase()}
        docsLinkPath="/docs/components/concepts/audit-log/"
      />
      <EntityList
        data={
          auditLogs?.items.map((log) => ({
            id: log.auditLogKey,
            operationType: log.operationType,
            entityType: log.entityType,
            result: log.result,
            actorId: log.actorId,
            timestamp: new Date(log.timestamp).toLocaleString(),
          })) || []
        }
        headers={[
          { header: t("operation"), key: "operationType" },
          { header: t("entity"), key: "entityType" },
          { header: t("status"), key: "result" },
          { header: t("actor"), key: "actorId" },
          { header: t("time"), key: "timestamp" },
        ]}
        loading={loading}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("operationsLogCouldNotLoad")}
          actionButton={{ label: tComponents("retry"), onClick: reload }}
        />
      )}
    </Page>
  );
};

export default List;
