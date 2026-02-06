/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import {
  ComposedModal,
  ModalHeader,
  ModalBody,
  Stack,
  StructuredListWrapper,
  StructuredListCell,
  StructuredListBody,
  StructuredListRow,
  InlineNotification,
  CodeSnippet,
} from "@carbon/react";
import { CheckmarkOutline, UserAvatar, EventSchedule, Link } from "@carbon/react/icons";
import { StatusIndicator } from "./StatusIndicator";
import { spaceAndCapitalize } from "src/utility/format/spaceAndCapitalize";
import styled from "styled-components";

const AlignedStructuredListWrapper = styled(StructuredListWrapper)`
  .cds--structured-list-tbody .cds--structured-list-row .cds--structured-list-td:first-child {
    width: 168px;
    min-width: 168px;
    max-width: 168px;
  }
  .cds--structured-list-tbody .cds--structured-list-row .cds--structured-list-td:last-child {
    width: auto;
  }
`;

const OWNER_TYPES = ["User", "Role", "Group", "Application"];
const RESOURCE_TYPES = ["ProcessDefinition", "ProcessInstance", "Task", "DecisionDefinition", "DecisionInstance"];

const pickBySeed = (seed: string, options: string[]) => {
  const hash = seed.split("").reduce((sum, char) => sum + char.charCodeAt(0), 0);
  return options[hash % options.length];
};

const generateOwnerId = (seed: string) => {
  const hash = seed.split("").reduce((sum, char) => sum + char.charCodeAt(0), 0);
  const ids = ["user-12345", "role-admin", "group-developers", "app-client-001"];
  return ids[hash % ids.length];
};

const generateResourceId = (seed: string) => {
  const hash = seed.split("").reduce((sum, char) => sum + char.charCodeAt(0), 0);
  const ids = ["process-def-001", "process-instance-123", "task-456", "decision-def-789", "decision-instance-012"];
  return ids[hash % ids.length];
};

const generatePermissions = (seed: string): string[] => {
  const hash = seed.split("").reduce((sum, char) => sum + char.charCodeAt(0), 0);
  const allPermissions = [
    "READ_PROCESS_INSTANCE",
    "UPDATE_PROCESS_INSTANCE",
    "DELETE_PROCESS_INSTANCE",
    "READ_TASK",
    "UPDATE_TASK",
    "DELETE_TASK",
    "READ_DECISION_INSTANCE",
    "READ_DECISION_DEFINITION",
  ];
  // Return 2-4 permissions based on hash
  const count = 2 + (hash % 3);
  const startIndex = hash % (allPermissions.length - count);
  return allPermissions.slice(startIndex, startIndex + count);
};

type AuditLogEntry = {
  auditLogKey: string;
  operationType: string;
  entityType: string;
  entityKey: string;
  result: "SUCCESS" | "FAIL";
  actorId?: string;
  timestamp: string;
};

type Props = {
  open: boolean;
  onClose: () => void;
  entry: AuditLogEntry | null;
};

const DetailsModal: FC<Props> = ({ open, onClose, entry }) => {
  if (!entry) {
    return null;
  }

  const formatOperationType = (type: string) => {
    return spaceAndCapitalize(type);
  };

  const formatDate = (timestamp: string) => {
    return new Date(timestamp).toLocaleString();
  };

  const hasProperties = entry.entityType === "AUTHORIZATION";
  const ownerType = hasProperties
    ? pickBySeed(`${entry.auditLogKey}-owner`, OWNER_TYPES)
    : null;
  const ownerId = hasProperties
    ? generateOwnerId(`${entry.auditLogKey}-owner-id`)
    : null;
  const resourceType = hasProperties
    ? pickBySeed(`${entry.auditLogKey}-resource`, RESOURCE_TYPES)
    : null;
  const resourceId = hasProperties
    ? generateResourceId(`${entry.auditLogKey}-resource-id`)
    : null;
  const permissions = hasProperties
    ? generatePermissions(`${entry.auditLogKey}-permissions`)
    : [];

  return (
    <ComposedModal size="md" open={open} onClose={onClose}>
      <ModalHeader
        title={`${formatOperationType(entry.operationType)} ${spaceAndCapitalize(entry.entityType)}`}
        closeModal={onClose}
      />
      <ModalBody>
        <Stack gap={5}>
          <Stack gap={1}>
            <AlignedStructuredListWrapper isCondensed isFlush>
              <StructuredListBody>
                <StructuredListRow>
                  <StructuredListCell>
                    <div
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: "var(--cds-spacing-03)",
                      }}
                    >
                      <Link />
                      Reference to entity
                    </div>
                  </StructuredListCell>
                  <StructuredListCell>
                    <CodeSnippet type="inline">{entry.entityKey}</CodeSnippet>
                  </StructuredListCell>
                </StructuredListRow>
                <StructuredListRow>
                  <StructuredListCell>
                    <div
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: "var(--cds-spacing-03)",
                      }}
                    >
                      <CheckmarkOutline />
                      Status
                    </div>
                  </StructuredListCell>
                  <StructuredListCell>
                    <StatusIndicator status={entry.result} />
                  </StructuredListCell>
                </StructuredListRow>
                <StructuredListRow>
                  <StructuredListCell>
                    <div
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: "var(--cds-spacing-03)",
                      }}
                    >
                      <UserAvatar />
                      Actor
                    </div>
                  </StructuredListCell>
                  <StructuredListCell>
                    {entry.actorId ? (
                      <CodeSnippet type="inline">{entry.actorId}</CodeSnippet>
                    ) : (
                      "–"
                    )}
                  </StructuredListCell>
                </StructuredListRow>
                <StructuredListRow>
                  <StructuredListCell>
                    <div
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: "var(--cds-spacing-03)",
                      }}
                    >
                      <EventSchedule />
                      Date
                    </div>
                  </StructuredListCell>
                  <StructuredListCell>
                    {formatDate(entry.timestamp)}
                  </StructuredListCell>
                </StructuredListRow>
              </StructuredListBody>
            </AlignedStructuredListWrapper>
          </Stack>
          {hasProperties && (
            <Stack gap={1}>
              <div
                style={{
                  fontSize: "var(--cds-heading-01-font-size)",
                  fontWeight: "var(--cds-heading-01-font-weight)",
                  lineHeight: "var(--cds-heading-01-line-height)",
                  letterSpacing: "var(--cds-heading-01-letter-spacing)",
                  color: "var(--cds-text-primary)",
                }}
              >
                Properties
              </div>
              <AlignedStructuredListWrapper isCondensed isFlush>
                <StructuredListBody>
                  <StructuredListRow>
                    <StructuredListCell>
                      Owner type
                    </StructuredListCell>
                    <StructuredListCell>
                      {ownerType}
                    </StructuredListCell>
                  </StructuredListRow>
                  <StructuredListRow>
                    <StructuredListCell>
                      Owner ID
                    </StructuredListCell>
                    <StructuredListCell>
                      <CodeSnippet type="inline">{ownerId}</CodeSnippet>
                    </StructuredListCell>
                  </StructuredListRow>
                  <StructuredListRow>
                    <StructuredListCell>
                      Resource type
                    </StructuredListCell>
                    <StructuredListCell>
                      {resourceType}
                    </StructuredListCell>
                  </StructuredListRow>
                  <StructuredListRow>
                    <StructuredListCell>
                      Resource ID
                    </StructuredListCell>
                    <StructuredListCell>
                      <CodeSnippet type="inline">{resourceId}</CodeSnippet>
                    </StructuredListCell>
                  </StructuredListRow>
                  <StructuredListRow>
                    <StructuredListCell>
                      Permissions
                    </StructuredListCell>
                    <StructuredListCell>
                      <div style={{ display: "flex", flexWrap: "wrap", gap: "var(--cds-spacing-02)" }}>
                        {permissions.map((permission, index) => (
                          <CodeSnippet key={index} type="inline">
                            {permission}
                          </CodeSnippet>
                        ))}
                      </div>
                    </StructuredListCell>
                  </StructuredListRow>
                </StructuredListBody>
              </AlignedStructuredListWrapper>
            </Stack>
          )}
          {entry.result === "FAIL" && (
            <InlineNotification
              kind="error"
              title="Operation failed"
              subtitle="This operation did not complete successfully."
              hideCloseButton
              lowContrast
            />
          )}
        </Stack>
      </ModalBody>
    </ComposedModal>
  );
};

export { DetailsModal };

