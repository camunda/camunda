/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import styled from "styled-components";
import { Tag } from "@carbon/react";
import { UseEntityModalCustomProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApi, useApiCall } from "src/utility/api/hooks";
import { searchMappingRule, MappingRule } from "src/utility/api/mappings";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import DropdownSearch from "src/components/form/DropdownSearch";
import FormModal from "src/components/modal/FormModal";
import { assignRoleMappingRule, Role } from "src/utility/api/roles";

const SelectedMappings = styled.div`
  margin-top: 0;
`;

const AssignMappingsModal: FC<
  UseEntityModalCustomProps<
    { id: Role["roleKey"] },
    { assignedMappingRules: MappingRule[] }
  >
> = ({ entity: role, assignedMappingRules, onSuccess, open, onClose }) => {
  const { t, Translate } = useTranslate("roles");
  const [selectedMappingRules, setSelectedMappingRules] = useState<
    MappingRule[]
  >([]);
  const [loadingAssignMappingRule, setLoadingAssignMappingRule] =
    useState(false);

  const {
    data: mappingSearchResults,
    loading,
    reload,
    error,
  } = useApi(searchMappingRule);

  const [callAssignMapping] = useApiCall(assignRoleMappingRule);

  const unassignedMappings =
    mappingSearchResults?.items.filter(
      ({ mappingRuleId }) =>
        !assignedMappingRules.some(
          (mapping) => mapping.mappingRuleId === mappingRuleId,
        ) &&
        !selectedMappingRules.some(
          (mapping) => mapping.mappingRuleId === mappingRuleId,
        ),
    ) || [];

  const onSelectMapping = (mapping: MappingRule) => {
    setSelectedMappingRules([...selectedMappingRules, mapping]);
  };

  const onUnselectMapping =
    ({ mappingRuleId }: MappingRule) =>
    () => {
      setSelectedMappingRules(
        selectedMappingRules.filter(
          (mapping) => mapping.mappingRuleId !== mappingRuleId,
        ),
      );
    };

  const canSubmit = role && selectedMappingRules.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignMappingRule(true);

    const results = await Promise.all(
      selectedMappingRules.map(({ mappingRuleId }) =>
        callAssignMapping({ mappingRuleId, roleId: role.id }),
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
      headline={t("assignMapping")}
      confirmLabel={t("assignMapping")}
      loading={loadingAssignMappingRule}
      loadingDescription={t("assigningMapping")}
      open={open}
      onSubmit={handleSubmit}
      submitDisabled={!canSubmit}
      onClose={onClose}
      overflowVisible
    >
      <p>
        <Translate i18nKey="searchAndAssignMappingToRole">
          Search and assign mapping to role
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
