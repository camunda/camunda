/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { usePagination } from "src/utility/api";
import { tenantQueries } from "src/utility/api/tenants/queries";
import { Dropdown } from "@carbon/react";
import useTranslate from "src/utility/localization";
import type { Tenant } from "@camunda/camunda-api-zod-schemas/8.10";

export type TenantDropdownProps = {
  tenantId: string | undefined;
  onChange: (tenantId: string | undefined) => void;
};

const ClusterVariableTenantDropdown: FC<TenantDropdownProps> = ({
  tenantId,
  onChange,
}) => {
  const { t } = useTranslate("clusterVariables");

  const { pageParams } = usePagination();
  const { data: tenants, isLoading: tenantLoading } = useQuery(
    tenantQueries.search(pageParams),
  );

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
