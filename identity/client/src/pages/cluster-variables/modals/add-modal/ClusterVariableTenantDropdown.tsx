/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect } from "react";
import { usePaginatedApi } from "src/utility/api";
import { searchTenant, Tenant } from "src/utility/api/tenants";
import { Dropdown } from "@carbon/react";
import useTranslate from "src/utility/localization";

export type TenantDropdownProps = {
  tenantId: string | undefined;
  onChange: (tenantId: string | undefined) => void;
};

const ClusterVariableTenantDropdown: FC<TenantDropdownProps> = ({
  tenantId,
  onChange,
}) => {
  const { t } = useTranslate("clusterVariables");

  const { data: tenants, loading: tenantLoading } =
    usePaginatedApi(searchTenant);

  // Set tenantId to first tenant if not set
  useEffect(() => {
    if (
      tenants?.items?.length &&
      (!tenantId ||
        !tenants.items.some((tenant) => tenant.tenantId === tenantId))
    ) {
      onChange(tenants.items[0].tenantId);
    }
  }, [tenants, tenantId, onChange]);

  return (
    <Dropdown
      id="cluster-variable-tenant-id-dropdown"
      label={t(
        tenantLoading
          ? "clusterVariableTenantIdLoadingPlaceholder"
          : "clusterVariableTenantIdPlaceholder",
      )}
      titleText={t("clusterVariableTenantId")}
      items={tenants?.items || []}
      onChange={({ selectedItem }) => onChange(selectedItem?.tenantId)}
      itemToString={(item: Tenant) => item.name || ""}
      selectedItem={tenants?.items.find(
        (tenant) => tenant.tenantId === tenantId,
      )}
    />
  );
};

export default ClusterVariableTenantDropdown;
