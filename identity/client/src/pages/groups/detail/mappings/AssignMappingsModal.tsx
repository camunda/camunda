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
import { searchMapping, Mapping } from "src/utility/api/mappings";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import styled from "styled-components";
import DropdownSearch from "src/components/form/DropdownSearch";
import FormModal from "src/components/modal/FormModal";
import { assignGroupMapping, Group } from "src/utility/api/groups";

const SelectedMappings = styled.div`
  margin-top: 0;
`;

const AssignMappingsModal: FC<
  UseEntityModalCustomProps<
    { id: Group["groupKey"] },
    { assignedMappings: Mapping[] }
  >
> = ({ entity: group, assignedMappings, onSuccess, open, onClose }) => {
  const { t, Translate } = useTranslate("groups");
  const [selectedMappings, setSelectedMappings] = useState<Mapping[]>([]);
  const [loadingAssignMapping, setLoadingAssignMapping] = useState(false);

  const {
    data: mappingSearchResults,
    loading,
    reload,
    error,
  } = useApi(searchMapping);

  const [callAssignMapping] = useApiCall(assignGroupMapping);

  const unassignedMappings =
    mappingSearchResults?.items.filter(
      ({ id }) =>
        !assignedMappings.some((mapping) => mapping.id === id) &&
        !selectedMappings.some((mapping) => mapping.id === id),
    ) || [];

  const onSelectMapping = (mapping: Mapping) => {
    setSelectedMappings([...selectedMappings, mapping]);
  };

  const onUnselectMapping =
    ({ id }: Mapping) =>
    () => {
      setSelectedMappings(
        selectedMappings.filter((mapping) => mapping.id !== id),
      );
    };

  const canSubmit = group && selectedMappings.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignMapping(true);

    const results = await Promise.all(
      selectedMappings.map(({ id }) =>
        callAssignMapping({ id, groupId: group.id }),
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
        <Translate i18nKey="searchAndAssignMappingToGroup">
          Search and assign mapping to group
        </Translate>
      </p>
      {selectedMappings.length > 0 && (
        <SelectedMappings>
          {selectedMappings.map((mapping) => (
            <Tag
              key={mapping.id}
              onClose={onUnselectMapping(mapping)}
              size="md"
              type="blue"
              filter
            >
              {mapping.id}
            </Tag>
          ))}
        </SelectedMappings>
      )}
      <DropdownSearch
        autoFocus
        items={unassignedMappings}
        itemTitle={({ id }) => id}
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
