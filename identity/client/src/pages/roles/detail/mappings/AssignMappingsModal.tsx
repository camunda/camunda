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
import { searchMapping, Mapping } from "src/utility/api/mappings";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import DropdownSearch from "src/components/form/DropdownSearch";
import FormModal from "src/components/modal/FormModal";
import { assignRoleMapping, Role } from "src/utility/api/roles";

const SelectedMappings = styled.div`
  margin-top: 0;
`;

const AssignMappingsModal: FC<
  UseEntityModalCustomProps<
    { id: Role["roleKey"] },
    { assignedMappings: Mapping[] }
  >
> = ({ entity: role, assignedMappings, onSuccess, open, onClose }) => {
  const { t, Translate } = useTranslate("roles");
  const [selectedMappings, setSelectedMappings] = useState<Mapping[]>([]);
  const [loadingAssignMapping, setLoadingAssignMapping] = useState(false);

  const {
    data: mappingSearchResults,
    loading,
    reload,
    error,
  } = useApi(searchMapping);

  const [callAssignMapping] = useApiCall(assignRoleMapping);

  const unassignedMappings =
    mappingSearchResults?.items.filter(
      ({ mappingId }) =>
        !assignedMappings.some((mapping) => mapping.mappingId === mappingId) &&
        !selectedMappings.some((mapping) => mapping.mappingId === mappingId),
    ) || [];

  const onSelectMapping = (mapping: Mapping) => {
    setSelectedMappings([...selectedMappings, mapping]);
  };

  const onUnselectMapping =
    ({ mappingId }: Mapping) =>
    () => {
      setSelectedMappings(
        selectedMappings.filter((mapping) => mapping.mappingId !== mappingId),
      );
    };

  const canSubmit = role && selectedMappings.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignMapping(true);

    const results = await Promise.all(
      selectedMappings.map(({ mappingId }) =>
        callAssignMapping({ mappingId, roleId: role.id }),
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
        <Translate i18nKey="searchAndAssignMappingToRole">
          Search and assign mapping to role
        </Translate>
      </p>
      {selectedMappings.length > 0 && (
        <SelectedMappings>
          {selectedMappings.map((mapping) => (
            <Tag
              key={mapping.mappingId}
              onClose={onUnselectMapping(mapping)}
              size="md"
              type="blue"
              filter
            >
              {mapping.mappingId}
            </Tag>
          ))}
        </SelectedMappings>
      )}
      <DropdownSearch
        autoFocus
        items={unassignedMappings}
        itemTitle={({ mappingId }) => mappingId}
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
