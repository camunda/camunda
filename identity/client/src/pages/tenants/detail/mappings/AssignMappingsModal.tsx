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
    { assignedMappingRules: MappingRule[] }
  >
> = ({ entity: tenant, assignedMappingRules, onSuccess, open, onClose }) => {
  const { t, Translate } = useTranslate("tenants");
  const [selectedMappingRules, setSelectedMappingRules] = useState<
    MappingRule[]
  >([]);
  const [loadingAssignMappingRule, setLoadingAssignMappingRule] =
    useState(false);

  const {
    data: mappingRuleSearchResults,
    loading,
    reload,
    error,
  } = useApi(searchMappingRule);

  const [callAssignMappingRule] = useApiCall(assignTenantMappingRule);

  const unassignedMappings =
    mappingRuleSearchResults?.items.filter(
      ({ mappingRuleId }) =>
        !assignedMappingRules.some(
          (mappingRule) => mappingRule.mappingRuleId === mappingRuleId,
        ) &&
        !selectedMappingRules.some(
          (mappingRule) => mappingRule.mappingRuleId === mappingRuleId,
        ),
    ) || [];

  const onSelectMapping = (mappingRule: MappingRule) => {
    setSelectedMappingRules([...selectedMappingRules, mappingRule]);
  };

  const onUnselectMapping =
    ({ mappingRuleId }: MappingRule) =>
    () => {
      setSelectedMappingRules(
        selectedMappingRules.filter(
          (mappingRule) => mappingRule.mappingRuleId !== mappingRuleId,
        ),
      );
    };

  const canSubmit = tenant && selectedMappingRules.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignMappingRule(true);

    const results = await Promise.all(
      selectedMappingRules.map(({ mappingRuleId }) =>
        callAssignMappingRule({ mappingRuleId, tenantId: tenant.id }),
      ),
    );

    setLoadingAssignMappingRule(false);

    if (results.every(({ success }) => success)) {
      onSuccess();
    }
  };

  useEffect(() => {
    if (open) {
      setSelectedMappingRules([]);
    }
  }, [open]);

  return (
    <FormModal
      headline={t("assignMappingRule")}
      confirmLabel={t("assignMappingRule")}
      loading={loadingAssignMappingRule}
      loadingDescription={t("assigningMappingRule")}
      open={open}
      onSubmit={handleSubmit}
      submitDisabled={!canSubmit}
      onClose={onClose}
      overflowVisible
    >
      <p>
        <Translate i18nKey="searchAndAssignMappingRuleToTenant">
          Search and assign mapping rule to tenant
        </Translate>
      </p>
      {selectedMappingRules.length > 0 && (
        <SelectedMappings>
          {selectedMappingRules.map((mapping) => (
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
        placeholder={t("searchByMappingRuleId")}
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
