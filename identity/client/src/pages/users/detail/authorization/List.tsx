/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import React, {Component, FC, useMemo, useState} from "react";
import {
  DocumentationDescription,
  NoDataBody,
  NoDataContainer,
  NoDataHeader,
} from "src/components/entityList";
import PatchModal from "src/pages/users/detail/authorization/PatchModal";
import {Add, Edit} from "@carbon/react/icons";
import useTranslate from "src/utility/localization";
import { User } from "src/utility/api/users";
import { getUserAuthorizations } from "src/utility/api/users/authorizations";
import { DocumentationLink } from "src/components/documentation";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import { useApi } from "src/utility/api";
import {
    DataTable,
    Table,
    TableBody,
    TableCell,
    TableContainer, TableExpandedRow,
    TableExpandHeader,
    TableExpandRow,
    TableHead,
    TableHeader,
    TableRow, TableToolbar, TableToolbarContent, TableToolbarSearch
} from "@carbon/react";

import {useEntityModal} from "src/components/modal";
import {PatchAuthorizationParams} from "src/utility/api/authorizations";


type AuthorizationsListProps = {
  user: User;
  loadingUser: boolean;
};

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

    const headers = [
        { header: t("ResourceType"), key: "resourceType" }
    ];

    const rows = authorizations == null ? [] : authorizations.items
    // Create a lookup map for rows by ID
    const dataMap = rows.reduce((map, item) => {
        map[item.id] = item;
        return map;
    }, {} as { [key: string]: typeof rows[0] });

    const [expandedRows, setExpandedRows] = useState({});

    const handleExpand = (rowId) => {
        setExpandedRows((prev) => ({
            ...prev,
            [rowId]: !prev[rowId],
        }));
    };

    const patch: PatchAuthorizationParams = {ownerKey: 1, action: "ADD", resourceType: "AUTH", permissions: []}
    const [handleAddRow, patchModal] = useEntityModal(PatchModal, reload, patch);

    const onEditPermission = (rowId: string, permissionIndex: number) => {
        // Find the row by its ID and remove the permission at the given index
        console.log(`Editing permission at index ${permissionIndex} for row with id: ${rowId}`);
        // Logic to update state or trigger an API call to remove the permission
    };

    const onAddPermission = (rowId: string) => {
      console.log(`Add permission row with id: ${rowId}`)
    };
    // const handleAddRow = () => {};

  const availablePermissionTypes = [
    'CREATE',
    'READ',
    'READ_INSTANCE',
    'READ_USER_TASK',
    'UPDATE',
    'DELETE',
    'DELETE_PROCESS',
    'DELETE_DRD',
    'DELETE_FORM',
  ];

  const checkMissingPermissions = (permissions) => {
    const existingTypes = permissions.map((perm) => perm.permissionType);
    return availablePermissionTypes.filter((type) => !existingTypes.includes(type));
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
          <TableContainer title="Authorizations" description="Authorizations's of the user">
              <DataTable rows={rows} headers={headers}>
                  {({ rows, headers, getHeaderProps, getRowProps, getTableProps }) => (
                      <div>
                          {/* Table Toolbar with Add Row Button */}
                          <TableToolbar>
                              <TableToolbarContent style={{ display: 'flex', justifyContent: 'flex-end', gap: '16px' }}>

                              <TableToolbarSearch/>

                                  <button
                                      onClick={handleAddRow}
                                      style={{
                                          border: 'none',
                                          background: '#0f62fe',
                                          color: 'white',
                                          cursor: 'pointer',
                                          padding: '8px 16px',
                                          borderRadius: '4px',
                                          display: 'flex',
                                          alignItems: 'center',
                                      }}
                                  >
                                      <Add style={{marginRight: '8px'}}/>
                                      Add New Authorization
                                  </button>

                                  </TableToolbarContent>
                          </TableToolbar>
                      <Table {...getTableProps()}>
                          <TableHead>
                              <TableRow>
                                  <TableExpandHeader />
                                  {headers.map((header) => (
                                      <TableHeader key={header.key} {...getHeaderProps({ header })}>
                                          {header.header}
                                      </TableHeader>
                                  ))}
                              </TableRow>
                          </TableHead>
                          <TableBody>
                              {rows.map((row) => {
                                  // Log the row object to the console for inspection
                                  console.log("Current Row:");
                                  console.log(row);

                                  return (
                                      <React.Fragment key={row.id}>
                                          <TableExpandRow
                                              {...getRowProps({row})}
                                              onClick={() => handleExpand(row.id)}
                                              isExpanded={!!expandedRows[row.id]}
                                          >
                                              <TableCell>
                                                  {row.cells.find((cell) => cell.info.header === 'resourceType')?.value}
                                              </TableCell>
                                          </TableExpandRow>
                                          {expandedRows[row.id] && (
                                              <TableExpandedRow colSpan={headers.length + 2}>
                                                  {/* Displaying list of permissions */}
                                                  {dataMap[row.id]?.permissions && (
                                                      <ul>
                                                          {dataMap[row.id].permissions.map((permission, index) => (
                                                              <React.Fragment key={index}>
                                                                  <li
                                                                      style={{
                                                                          display: 'flex',
                                                                          justifyContent: 'space-between',
                                                                          alignItems: 'center',
                                                                          padding: '10px 0',
                                                                      }}
                                                                  >
                                                                      <div>
                                                                          <strong>Permission Type:</strong> {permission.permissionType}
                                                                          <br />
                                                                          <strong>Resource IDs:</strong> {permission.resourceIds.join(', ')}
                                                                      </div>
                                                                      {/* Delete button at the end */}
                                                                      <button
                                                                          type="button"
                                                                          onClick={() => onEditPermission(row.id, index)}
                                                                          style={{
                                                                              border: 'none',
                                                                              background: 'none',
                                                                              cursor: 'pointer',
                                                                          }}
                                                                          aria-label="Delete permission"
                                                                      >
                                                                          <Edit />
                                                                      </button>
                                                                  </li>
                                                                  {/* Divider between permission rows */}
                                                                  {index < dataMap[row.id].permissions.length - 1 && (
                                                                      <hr style={{ border: 'none', borderTop: '1px solid #ccc', margin: '5px 0' }} />
                                                                  )}
                                                              </React.Fragment>
                                                          ))}
                                                      </ul>
                                                  )}
                                                {/* Add button if not all permissions are present */}
                                                {checkMissingPermissions(dataMap[row.id].permissions).length > 0 && (
                                                    <div style={{ marginTop: '16px', textAlign: 'right' }}>
                                                      <button
                                                          type="button"
                                                          onClick={() => onAddPermission(row.id)}
                                                          style={{
                                                            border: 'none',
                                                            background: '#0f62fe',
                                                            color: 'white',
                                                            cursor: 'pointer',
                                                            padding: '8px 16px',
                                                            borderRadius: '4px',
                                                          }}
                                                          aria-label="Add permission"
                                                      >
                                                        <Add style={{ marginRight: '8px' }} />
                                                        Add Permission
                                                      </button>
                                                    </div>
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



