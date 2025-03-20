/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import { Tag } from "@carbon/react";
import { UseEntityModalCustomProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApi, useApiCall } from "src/utility/api/hooks";
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

  const {
    data: roleSearchResults,
    loading,
    reload,
    error,
  } = useApi(searchRoles);

  const [callAssignRole] = useApiCall(assignTenantRole);

  const unassignedRoles =
    roleSearchResults?.items.filter(
      ({ key }) =>
        !assignedRoles.some((role) => role.key === key) &&
        !selectedRoles.some((role) => role.key === key),
    ) || [];

  const onSelectRole = (role: Role) => {
    setSelectedRoles([...selectedRoles, role]);
  };

  const onUnselectRole =
    ({ key }: Role) =>
    () => {
      setSelectedRoles(selectedRoles.filter((role) => role.key !== key));
    };

  const canSubmit = tenant && selectedRoles.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignRole(true);

    const results = await Promise.all(
      selectedRoles.map(({ roleKey }) =>
        callAssignRole({ roleKey, tenantId: tenant.id }),
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
              key={role.roleKey}
              onClose={onUnselectRole(role)}
              size="md"
              type="blue"
              filter
            >
              {role.key}
            </Tag>
          ))}
        </SelectedRoles>
      )}
      <DropdownSearch
        autoFocus
        items={unassignedRoles}
        itemTitle={({ key }) => String(key)}
        itemSubTitle={({ name }) => name}
        placeholder={t("searchByRoleId")}
        onSelect={onSelectRole}
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
