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
import { searchMappingRule, MappingRule } from "src/utility/api/mappings";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import styled from "styled-components";
import DropdownSearch from "src/components/form/DropdownSearch";
import FormModal from "src/components/modal/FormModal";
import { assignTenantMappingRule, Tenant } from "src/utility/api/tenants";

const SelectedMappings = styled.div`
  margin-top: 0;
`;

const AssignMappingsModal: FC<
  UseEntityModalCustomProps<
    { id: Tenant["tenantKey"] },
    { assignedMappings: MappingRule[] }
  >
> = ({ entity: tenant, assignedMappings, onSuccess, open, onClose }) => {
  const { t, Translate } = useTranslate("tenants");
  const [selectedMappings, setSelectedMappings] = useState<MappingRule[]>([]);
  const [loadingAssignMapping, setLoadingAssignMapping] = useState(false);

  const {
    data: mappingSearchResults,
    loading,
    reload,
    error,
  } = useApi(searchMappingRule);

  const [callAssignMapping] = useApiCall(assignTenantMappingRule);

  const unassignedMappings =
    mappingSearchResults?.items.filter(
      ({ mappingRuleId }) =>
        !assignedMappings.some(
          (mapping) => mapping.mappingRuleId === mappingRuleId,
        ) &&
        !selectedMappings.some(
          (mapping) => mapping.mappingRuleId === mappingRuleId,
        ),
    ) || [];

  const onSelectMapping = (mapping: MappingRule) => {
    setSelectedMappings([...selectedMappings, mapping]);
  };

  const onUnselectMapping =
    ({ mappingRuleId }: MappingRule) =>
    () => {
      setSelectedMappings(
        selectedMappings.filter(
          (mapping) => mapping.mappingRuleId !== mappingRuleId,
        ),
      );
    };

  const canSubmit = tenant && selectedMappings.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignMapping(true);

    const results = await Promise.all(
      selectedMappings.map(({ mappingRuleId }) =>
        callAssignMapping({ mappingRuleId, tenantId: tenant.id }),
      ),
    );

    setLoadingAssignMapping(false);

    if (results.every(({ success }) => success)) {
      onSuccess();
    }
  };

  useEffect(() => {
    if (open) {
      setSelectedMappings([]);
    }
  }, [open]);

  return (
    <FormModal
      headline={t("assignMapping")}
      confirmLabel={t("assignMapping")}
      loading={loadingAssignMapping}
      loadingDescription={t("assigningMapping")}
      open={open}
      onSubmit={handleSubmit}
      submitDisabled={!canSubmit}
      onClose={onClose}
      overflowVisible
    >
      <p>
        <Translate i18nKey="searchAndAssignMappingToTenant">
          Search and assign mapping to tenant
        </Translate>
      </p>
      {selectedMappings.length > 0 && (
        <SelectedMappings>
          {selectedMappings.map((mapping) => (
            <Tag
              key={mapping.mappingRuleId}
              onClose={onUnselectMapping(mapping)}
              size="md"
              type="blue"
              filter
            >
              {mapping.mappingRuleId}
            </Tag>
          ))}
        </SelectedMappings>
      )}
      <DropdownSearch
        autoFocus
        items={unassignedMappings}
        itemTitle={({ mappingRuleId }) => mappingRuleId}
        itemSubTitle={({ name }) => name}
        placeholder={t("searchByMappingId")}
        onSelect={onSelectMapping}
      />
      {!loading && error && (
        <TranslatedErrorInlineNotification
          title={t("mappingsCouldNotLoad")}
          actionButton={{ label: t("retry"), onClick: reload }}
        />
      )}
    </FormModal>
  );
};

export default AssignMappingsModal;
