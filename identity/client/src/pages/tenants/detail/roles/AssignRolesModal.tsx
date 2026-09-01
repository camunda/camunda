/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import { UseEntityModalCustomProps } from "src/components/modal";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { tenantMutations } from "src/utility/api/tenants/mutations";
import { RoleMultiSelect } from "src/components/form/entitySelection/RoleSelection";
import FormModal from "src/components/modal/FormModal";
import type { Role, Tenant } from "@camunda/camunda-api-zod-schemas/8.10";

const AssignRolesModal: FC<
  UseEntityModalCustomProps<
    { id: Tenant["tenantId"] },
    { assignedRoles: Role[] }
  >
> = ({ entity: tenant, assignedRoles, onSuccess, open, onClose }) => {
  const { t, Translate } = useTranslate("tenants");
  const [selectedRoles, setSelectedRoles] = useState<Role[]>([]);
  const [loadingAssignRole, setLoadingAssignRole] = useState(false);

  const qc = useQueryClient();
  const { mutateAsync: callAssignRole } = useMutation(
    tenantMutations.assignRole(qc),
  );

  const canSubmit = tenant && selectedRoles.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignRole(true);
    try {
      await Promise.all(
        selectedRoles.map(({ roleId }) =>
          callAssignRole({ roleId, tenantId: tenant.id }),
        ),
      );
      onSuccess();
    } catch {
      // error handled globally
    } finally {
      setLoadingAssignRole(false);
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
      <RoleMultiSelect
        value={selectedRoles}
        onChange={setSelectedRoles}
        excluded={assignedRoles}
        autoFocus
      />
    </FormModal>
  );
};

export default AssignRolesModal;
