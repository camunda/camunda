/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type { AuditLog } from "@camunda/camunda-api-zod-schemas/8.9/audit-log";
import { OwnerInfo, PropertyText } from "./components/styled";
import { CodeSnippet } from "@carbon/react";
import { spaceAndCapitalize } from "src/utility/format/spaceAndCapitalize";
import { AuditLogOperationType } from "@camunda/camunda-api-zod-schemas/8.9";
import useTranslate from "src/utility/localization";

type Props = {
  item: AuditLog;
};

const assignOperations: AuditLogOperationType[] = ["ASSIGN", "UNASSIGN"];

const CellProperty: React.FC<Props> = ({ item }) => {
  const { t } = useTranslate("operationsLog");

  const showProperty =
    (item.entityType === "AUTHORIZATION" && item.operationType === "CREATE") ||
    assignOperations.includes(item.operationType);

  if (item.result === "FAIL") {
    return (
      <div>
        <PropertyText>{t("errorCode")}</PropertyText>
        {item.entityDescription}
      </div>
    );
  } else if (showProperty) {
    return (
      <div>
        <PropertyText>
          {item.entityType === "AUTHORIZATION" ? t("owner") : t("assignee")}
        </PropertyText>
        <OwnerInfo>
          {item.relatedEntityType
            ? spaceAndCapitalize(item.relatedEntityType)
            : "-"}{" "}
          <CodeSnippet type="inline" hideCopyButton>
            {item.relatedEntityKey}
          </CodeSnippet>
        </OwnerInfo>
      </div>
    );
  } else {
    return "-";
  }
};

export { CellProperty };
