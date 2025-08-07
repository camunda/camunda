/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useCallback, useEffect, useState } from "react";
import styled from "styled-components";
import { Tag } from "@carbon/react";
import { UseEntityModalCustomProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApi, useApiCall } from "src/utility/api";
import { searchMappingRule, MappingRule } from "src/utility/api/mapping-rules";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import DropdownSearch from "src/components/form/DropdownSearch";
import FormModal from "src/components/modal/FormModal";
import { assignRoleMappingRule, Role } from "src/utility/api/roles";

const SelectedMappingRules = styled.div`
  margin-top: 0;
`;

const AssignMappingRulesModal: FC<
  UseEntityModalCustomProps<
    { id: Role["roleId"] },
    { assignedMappingRules: MappingRule[] }
  >
> = ({ entity: role, assignedMappingRules, onSuccess, open, onClose }) => {
  const { t, Translate } = useTranslate("roles");
  const [selectedMappingRules, setSelectedMappingRules] = useState<
    MappingRule[]
  >([]);
  const [loadingAssignMappingRule, setLoadingAssignMappingRule] =
    useState(false);

  const [mappingRuleFilter, setMappingRuleFilter] = useState({});
  const handleMappingRuleFilterChange = (search: string) => {
    if (!search.trim()) {
      setMappingRuleFilter({});
      return;
    }
    setMappingRuleFilter({ filter: { name: search } });
  };

  const {
    data: mappingRuleSearchResults,
    loading,
    reload,
    error,
  } = useApi(searchMappingRule, mappingRuleFilter);

  const [callAssignMappingRule] = useApiCall(assignRoleMappingRule);

  const unassignedFilter = useCallback(
    ({ mappingRuleId }: MappingRule) =>
      !assignedMappingRules.some(
        (mappingRule) => mappingRule.mappingRuleId === mappingRuleId,
      ) &&
      !selectedMappingRules.some(
        (mappingRule) => mappingRule.mappingRuleId === mappingRuleId,
      ),
    [assignedMappingRules, selectedMappingRules],
  );

  const onSelectMappingRule = (mappingRule: MappingRule) => {
    setSelectedMappingRules([...selectedMappingRules, mappingRule]);
  };

  const onUnselectMappingRule =
    ({ mappingRuleId }: MappingRule) =>
    () => {
      setSelectedMappingRules(
        selectedMappingRules.filter(
          (mappingRule) => mappingRule.mappingRuleId !== mappingRuleId,
        ),
      );
    };

  const canSubmit = role && selectedMappingRules.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignMappingRule(true);

    const results = await Promise.all(
      selectedMappingRules.map(({ mappingRuleId }) =>
        callAssignMappingRule({
          mappingRuleId: mappingRuleId,
          roleId: role.id,
        }),
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
        <Translate i18nKey="searchAndAssignMappingRuleToRole">
          Search and assign mapping rule to role
        </Translate>
      </p>
      {selectedMappingRules.length > 0 && (
        <SelectedMappingRules>
          {selectedMappingRules.map((mappingRule) => (
            <Tag
              key={mappingRule.mappingRuleId}
              onClose={onUnselectMappingRule(mappingRule)}
              size="md"
              type="blue"
              filter
            >
              {mappingRule.mappingRuleId}
            </Tag>
          ))}
        </SelectedMappingRules>
      )}
      <DropdownSearch
        autoFocus
        items={mappingRuleSearchResults?.items || []}
        itemTitle={({ mappingRuleId }) => mappingRuleId}
        itemSubTitle={({ name }) => name}
        placeholder={t("searchByMappingRuleId")}
        onSelect={onSelectMappingRule}
        onChange={handleMappingRuleFilterChange}
        filter={unassignedFilter}
      />
      {!loading && error && (
        <TranslatedErrorInlineNotification
          title={t("mappingRulesCouldNotLoad")}
          actionButton={{ label: t("retry"), onClick: reload }}
        />
      )}
    </FormModal>
  );
};

export default AssignMappingRulesModal;
