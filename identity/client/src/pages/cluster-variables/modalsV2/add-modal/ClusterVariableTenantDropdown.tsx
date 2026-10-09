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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@camunda/design-system";
import FormField from "src/components/formV2/FormField";
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
    <FormField label={t("clusterVariableTenantId")}>
      {({ id }) => (
        <Select
          value={
            tenants?.items.some((tenant) => tenant.tenantId === tenantId)
              ? tenantId
              : ""
          }
          onValueChange={(value) => onChange(value || undefined)}
        >
          <SelectTrigger id={id} className="w-full">
            <SelectValue
              placeholder={t(
                tenantLoading
                  ? "clusterVariableTenantIdLoadingPlaceholder"
                  : "clusterVariableTenantIdPlaceholder",
              )}
            />
          </SelectTrigger>
          <SelectContent>
            {(tenants?.items ?? []).map((tenant) => (
              <SelectItem key={tenant.tenantId} value={tenant.tenantId}>
                {tenant.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      )}
    </FormField>
  );
};

export default ClusterVariableTenantDropdown;
