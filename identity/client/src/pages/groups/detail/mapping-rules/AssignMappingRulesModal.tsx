/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useCallback, useEffect, useState } from "react";
import { Tag } from "@carbon/react";
import { UseEntityModalCustomProps } from "src/components/modal";
import useTranslate from "src/utility/localization";
import { useApi, useApiCall } from "src/utility/api";
import { searchMappingRule, MappingRule } from "src/utility/api/mapping-rules";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import styled from "styled-components";
import DropdownSearch from "src/components/form/DropdownSearch";
import FormModal from "src/components/modal/FormModal";
import { assignGroupMappingRule, Group } from "src/utility/api/groups";
import { useNotifications } from "src/components/notifications";

const SelectedMappingRules = styled.div`
  margin-top: 0;
`;

const AssignMappingRulesModal: FC<
  UseEntityModalCustomProps<
    { id: Group["groupId"] },
    { assignedMappingRules: MappingRule[] }
  >
> = ({ entity: group, assignedMappingRules, onSuccess, open, onClose }) => {
  const { t, Translate } = useTranslate("groups");
  const { enqueueNotification } = useNotifications();
  const [selectedMappingRules, setSelectedMappingRules] = useState<
    MappingRule[]
  >([]);
  const [loadingAssignMappingRule, setLoadingAssignMappingRule] =
    useState(false);

  const [mappingRuleFilter, setMappingRuleFilter] = useState({});
  const handleMappingRuleFilterChange = useCallback((search: string) => {
    if (!search.trim()) {
      setMappingRuleFilter({});
      return;
    }
    setMappingRuleFilter({ filter: { name: search } });
  }, []);

  const {
    data: mappingRuleSearchResults,
    loading,
    reload,
    error,
  } = useApi(searchMappingRule, mappingRuleFilter);

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

  const [callAssignMappingRule] = useApiCall(assignGroupMappingRule);

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

  const canSubmit = group && selectedMappingRules.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignMappingRule(true);

    const results = await Promise.all(
      selectedMappingRules.map(({ mappingRuleId }) =>
        callAssignMappingRule({
          mappingRuleId: mappingRuleId,
          groupId: group.id,
        }),
      ),
    );

    setLoadingAssignMappingRule(false);

    if (results.every(({ success }) => success)) {
      if (selectedMappingRules.length === 1) {
        enqueueNotification({
          kind: "success",
          title: t("mappingRuleAssigned"),
          subtitle: t("mappingRuleAssignedSuccessfully"),
        });
      } else {
        enqueueNotification({
          kind: "success",
          title: t("mappingRulesAssigned"),
          subtitle: t("mappingRulesAssignedSuccessfully"),
        });
      }
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
        <Translate i18nKey="searchAndAssignMappingRuleToGroup">
          Search and assign mapping rule to group
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
        onChange={handleMappingRuleFilterChange}
        items={mappingRuleSearchResults?.items || []}
        filter={unassignedFilter}
        itemTitle={({ mappingRuleId }) => mappingRuleId}
        itemSubTitle={({ name }) => name}
        placeholder={t("searchByMappingRuleId")}
        onSelect={onSelectMappingRule}
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
