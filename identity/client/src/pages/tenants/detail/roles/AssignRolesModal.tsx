/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useCallback, useEffect, useState } from "react";
import { Tag } from "@carbon/react";
import { UseEntityModalCustomProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApi, useApiCall } from "src/utility/api";
import { searchRoles, Role } from "src/utility/api/roles";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import styled from "styled-components";
import DropdownSearch from "src/components/form/DropdownSearch";
import FormModal from "src/components/modal/FormModal";
import { assignTenantRole, Tenant } from "src/utility/api/tenants";

const SelectedRoles = styled.div`
  margin-top: 0;
`;

const AssignRolesModal: FC<
  UseEntityModalCustomProps<
    { id: Tenant["tenantKey"] },
    { assignedRoles: Role[] }
  >
> = ({ entity: tenant, assignedRoles, onSuccess, open, onClose }) => {
  const { t, Translate } = useTranslate("tenants");
  const [selectedRoles, setSelectedRoles] = useState<Role[]>([]);
  const [loadingAssignRole, setLoadingAssignRole] = useState(false);

  const [search, setSearch] = useState<Record<string, unknown>>({});
  const handleSearchChange = (search: string) => {
    if (search === "") {
      setSearch({});
      return;
    }
    setSearch({ filter: { name: search } });
  };

  const {
    data: roleSearchResults,
    loading,
    reload,
    error,
  } = useApi(searchRoles, search);

  const [callAssignRole] = useApiCall(assignTenantRole);

  const unassignedFilter = useCallback(
    ({ roleId }: Role) =>
      !assignedRoles.some((role) => role.roleId === roleId) &&
      !selectedRoles.some((role) => role.roleId === roleId),
    [assignedRoles, selectedRoles],
  );

  const onSelectRole = (role: Role) => {
    setSelectedRoles([...selectedRoles, role]);
  };

  const onUnselectRole =
    ({ roleId }: Role) =>
    () => {
      setSelectedRoles(selectedRoles.filter((role) => role.roleId !== roleId));
    };

  const canSubmit = tenant && selectedRoles.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignRole(true);

    const results = await Promise.all(
      selectedRoles.map(({ roleId }) =>
        callAssignRole({ roleId, tenantId: tenant.id }),
      ),
    );

    setLoadingAssignRole(false);

    if (results.every(({ success }) => success)) {
      onSuccess();
    }
  };

  useEffect(() => {
    if (open) {
      setSelectedRoles([]);
    }
  }, [open]);

  return (
    <FormModal
      headline={t("assignRole")}
      confirmLabel={t("assignRole")}
      loading={loadingAssignRole}
      loadingDescription={t("assigningRole")}
      open={open}
      onSubmit={handleSubmit}
      submitDisabled={!canSubmit}
      onClose={onClose}
      overflowVisible
    >
      <p>
        <Translate>Search and assign role to tenant</Translate>
      </p>
      {selectedRoles.length > 0 && (
        <SelectedRoles>
          {selectedRoles.map((role) => (
            <Tag
              key={role.roleId}
              onClose={onUnselectRole(role)}
              size="md"
              type="blue"
              filter
            >
              {role.roleId}
            </Tag>
          ))}
        </SelectedRoles>
      )}
      <DropdownSearch
        autoFocus
        items={roleSearchResults?.items || []}
        itemTitle={({ roleId }) => roleId}
        itemSubTitle={({ name }) => name}
        placeholder={t("searchByRoleId")}
        onSelect={onSelectRole}
        onChange={handleSearchChange}
        filter={unassignedFilter}
      />
      {!loading && error && (
        <TranslatedErrorInlineNotification
          title={t("rolesCouldNotLoad")}
          actionButton={{ label: t("retry"), onClick: reload }}
        />
      )}
    </FormModal>
  );
};

export default AssignRolesModal;
