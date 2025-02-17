/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC } from "react";
import EntityDetail from "src/components/entityDetail";
import useTranslate from "src/utility/localization";
import { Tenant } from "src/utility/api/tenants";
import { CodeSnippet, InlineLoading } from "@carbon/react";
import { cssSize } from "src/utility/style.ts";
import styled from "styled-components";

type DetailsProps = {
  tenant?: Tenant;
  loading: boolean;
};

const StyledCodeSnippet = styled(CodeSnippet)`
  max-width: ${cssSize(48)};
`;

const Details: FC<DetailsProps> = ({ tenant, loading }) => {
  const { t } = useTranslate("tenants");

  if (loading) {
    return <InlineLoading description={t("Loading tenant details...")} />;
  }

  if (!tenant) {
    return <div>{t("No tenant details available.")}</div>;
  }

  return (
    <EntityDetail
      label={t("Tenant details")}
      data={[
        {
          label: t("Name"),
          value: tenant.name,
        },
        {
          label: t("Tenant ID"),
          value: (
            <StyledCodeSnippet type="single">
              {tenant.tenantId}
            </StyledCodeSnippet>
          ),
        },
        {
          label: t("Description"),
          value: tenant.description,
        },
      ]}
    />
  );
};

export default Details;
