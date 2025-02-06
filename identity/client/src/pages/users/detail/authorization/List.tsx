/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import React, { FC, useState } from "react";
import {
  DocumentationDescription,
  NoDataBody,
  NoDataContainer,
  NoDataHeader,
} from "src/components/entityList";
import PatchModal, {
  ResourceType,
} from "src/pages/users/detail/authorization/PatchModal";
import { Edit } from "@carbon/react/icons";
import useTranslate from "src/utility/localization";
import { User } from "src/utility/api/users";
import { getUserAuthorizations } from "src/utility/api/users/authorizations";
import { DocumentationLink } from "src/components/documentation";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import { useApi } from "src/utility/api";
import {
  Button,
  DataTable,
  ListItem,
  Section,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableExpandedRow,
  TableExpandHeader,
  TableExpandRow,
  TableHead,
  TableHeader,
  TableRow,
  TableToolbar,
  TableToolbarContent,
  TableToolbarSearch,
  UnorderedList,
} from "@carbon/react";

import { useEntityModal } from "src/components/modal";
import { Authorization } from "src/utility/api/authorizations";
import { DataTableRenderProps } from "@carbon/react/lib/components/DataTable/DataTable";
import styled from "styled-components";
import { gray30, spacing02, spacing04, spacing05 } from "@carbon/elements";

type AuthorizationsListProps = {
  user: User;
  loadingUser: boolean;
};

const ToolbarContent = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: ${spacing05};
`;

const StyledListItem = styled(ListItem)`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-left: ${spacing04};
`;
const StyledDivider = styled(Section)`
  margin: ${spacing02} 0;
  border-top: 1px solid ${gray30};
`;

const List: FC<AuthorizationsListProps> = ({ user, loadingUser }) => {
  const { t, Translate } = useTranslate();

  const {
    data: authorizations,
    loading: loadingAuthorizations,
    success,
    reload,
  } = useApi(getUserAuthorizations, { key: user.key });

  const loading = loadingUser || loadingAuthorizations;

  const areAuthorizationsEmpty =
    !authorizations || authorizations.items.length === 0;

  const headers = [{ header: t("ResourceType"), key: "resourceType" }];

  const rowsWithoutId: Authorization[] =
    authorizations == null ? [] : authorizations.items;

  const rows = rowsWithoutId.map((item, index) => ({
    ...item,
    id: String(index),
  }));

  // Create a lookup map for rows by ID
  const dataMap = rows.reduce(
    (map, item) => {
      map[item.id] = item;
      return map;
    },
    {} as { [key: string]: (typeof rows)[0] },
  );

  const [expandedRows, setExpandedRows] = useState<Record<string, boolean>>({});

  const handleExpand = (rowId: string) => {
    setExpandedRows((prev) => ({
      ...prev,
      [rowId]: !prev[rowId],
    }));
  };

  const [handleOpenPatchModal, patchModal] = useEntityModal(PatchModal, reload);

  const onEditPermission = (rowId: string, permissionIndex: number) => {
    // Find the row by its ID and remove the permission at the given index
    // Logic to update state or trigger an API call to remove the permission
    handleOpenPatchModal({
      ownerKey: user.key,
      resourceType:
        ResourceType[dataMap[rowId].resourceType as keyof typeof ResourceType],
      permissionType:
        dataMap[rowId].permissions[permissionIndex].permissionType,
      currentAuthorizations: rowsWithoutId,
    });
  };

  const documentationReference = (
    <Translate>
      Learn more about assigning authorizations to users in our{" "}
      <DocumentationLink path="/identity/user-guide/assigning-an-authorization-to-a-user" />
      .
    </Translate>
  );

  return (
    <>
      <TableContainer
        title="Authorizations"
        description="Authorizations's of the user"
      >
        <DataTable<Authorization> rows={rows} headers={headers}>
          {({
            headers,
            rows,
            getHeaderProps,
            getRowProps,
            getTableProps,
          }: DataTableRenderProps<Authorization, unknown[]>) => (
            <div>
              <TableToolbar>
                <TableToolbarContent as={ToolbarContent}>
                  <TableToolbarSearch />
                  <Button
                    onClick={() =>
                      handleOpenPatchModal({
                        ownerKey: user.key,
                        resourceType: null,
                        permissionType: null,
                        currentAuthorizations: rows,
                      })
                    }
                  >
                    {t("Add Authorizations")}
                  </Button>
                </TableToolbarContent>
              </TableToolbar>
              <Table {...getTableProps()}>
                <TableHead>
                  <TableRow>
                    <TableExpandHeader />
                    {headers.map((header) => (
                      <TableHeader
                        {...getHeaderProps({ header })}
                        key={header.key}
                      >
                        {header.header}
                      </TableHeader>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((row) => {
                    return (
                      <React.Fragment key={row.id}>
                        <TableExpandRow
                          {...getRowProps({ row })}
                          key={row.key}
                          onClick={() => handleExpand(row.id)}
                          isExpanded={expandedRows[row.id]}
                        >
                          <TableCell>
                            {
                              row.cells.find(
                                (cell) => cell.info.header === "resourceType",
                              )?.value
                            }
                          </TableCell>
                        </TableExpandRow>
                        {expandedRows[row.id] && (
                          <TableExpandedRow colSpan={headers.length + 2}>
                            {/* Displaying list of permissions */}
                            {dataMap[row.id]?.permissions && (
                              <ul>
                                {dataMap[row.id].permissions.map(
                                  (permission, index) => (
                                    <React.Fragment key={index}>
                                      <UnorderedList>
                                        <StyledListItem>
                                          <div>
                                            <strong>Permission Type:</strong>{" "}
                                            {permission.permissionType}
                                            <br />
                                            <strong>Resource IDs:</strong>{" "}
                                            {permission.resourceIds.join(", ")}
                                          </div>
                                          <Button
                                            kind="ghost"
                                            size="sm"
                                            iconDescription={t(
                                              "Edit permission",
                                            )}
                                            onClick={() =>
                                              onEditPermission(row.id, index)
                                            }
                                          >
                                            <Edit />
                                          </Button>
                                        </StyledListItem>
                                      </UnorderedList>
                                      {index <
                                        dataMap[row.id].permissions.length -
                                          1 && <StyledDivider />}
                                    </React.Fragment>
                                  ),
                                )}
                              </ul>
                            )}
                          </TableExpandedRow>
                        )}
                      </React.Fragment>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
          )}
        </DataTable>
      </TableContainer>

      {success && !areAuthorizationsEmpty && (
        <DocumentationDescription>
          {documentationReference}
        </DocumentationDescription>
      )}
      {!loading && areAuthorizationsEmpty && (
        <div>
          {success && (
            <NoDataContainer>
              <NoDataHeader>
                <Translate>No authorizations assigned to this user</Translate>
              </NoDataHeader>
              <NoDataBody>{documentationReference}</NoDataBody>
            </NoDataContainer>
          )}
        </div>
      )}
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title="The list of authorizations could not be loaded."
          actionButton={{ label: "Retry", onClick: reload }}
        />
      )}
      {patchModal}
    </>
  );
};

export default List;
