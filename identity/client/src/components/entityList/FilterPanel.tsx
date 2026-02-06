/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState, useMemo } from "react";
import {
  MultiSelect,
  Dropdown,
  TextInput,
  Button,
} from "@carbon/react";
import styled from "styled-components";
import useTranslate from "src/utility/localization";
import { DateRangeField } from "src/components/dateRangeField";

const FilterPanelContainer = styled.div`
  width: 280px;
  min-width: 280px;
  flex-shrink: 0;
  padding-right: var(--cds-spacing-06);
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-05);
  overflow-y: auto;
`;

const FilterSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-03);
`;


const FilterTitle = styled.h3`
  font-size: var(--cds-heading-compact-01-font-size);
  font-weight: var(--cds-heading-compact-01-font-weight);
  line-height: var(--cds-heading-compact-01-line-height);
  letter-spacing: var(--cds-heading-compact-01-letter-spacing);
  margin: 0;
  color: var(--cds-text-primary);
`;

const ResetButtonWrapper = styled.div`
  width: 100%;
  
  button {
    width: 100%;
    justify-content: center;
  }
`;

export type FilterValues = {
  operationType?: string[];
  operationEntity?: string;
  operationStatus?: string[];
  actor?: string;
  dateRange?: {
    startDate?: Date;
    endDate?: Date;
  };
  ownerType?: string;
  ownerId?: string;
};

type FilterPanelProps = {
  onFilterChange: (filters: FilterValues) => void;
  operationTypeOptions?: string[];
  operationEntityOptions?: string[];
  operationStatusOptions?: string[];
};

const OWNER_TYPES = ["User", "Group", "Role", "Mapping rule"];

const FilterPanel: FC<FilterPanelProps> = ({ 
  onFilterChange,
  operationTypeOptions = [],
  operationEntityOptions = [],
  operationStatusOptions = [],
}) => {
  const { t } = useTranslate("operationsLog");
  const { t: tComponents } = useTranslate("components");

  const [operationType, setOperationType] = useState<string[]>([]);
  const [operationEntity, setOperationEntity] = useState<string>("");
  const [operationStatus, setOperationStatus] = useState<string[]>([]);
  const [actor, setActor] = useState<string>("");
  const [dateRange, setDateRange] = useState<{
    startDate?: Date;
    endDate?: Date;
  }>({});
  const [ownerType, setOwnerType] = useState<string>("");
  const [ownerId, setOwnerId] = useState<string>("");
  const [isDateRangeModalOpen, setIsDateRangeModalOpen] = useState<boolean>(false);

  // Check if any filters are applied
  const hasActiveFilters = useMemo(() => {
    return (
      operationType.length > 0 ||
      operationEntity !== "" ||
      operationStatus.length > 0 ||
      actor !== "" ||
      dateRange.startDate !== undefined ||
      dateRange.endDate !== undefined ||
      ownerType !== "" ||
      ownerId !== ""
    );
  }, [operationType, operationEntity, operationStatus, actor, dateRange, ownerType, ownerId]);

  const handleResetFilters = () => {
    setOperationType([]);
    setOperationEntity("");
    setOperationStatus([]);
    setActor("");
    setDateRange({});
    setOwnerType("");
    setOwnerId("");
    setIsDateRangeModalOpen(false);
    onFilterChange({});
  };

  const handleOperationTypeChange = (selectedItems: string[]) => {
    setOperationType(selectedItems);
    onFilterChange({
      operationType: selectedItems.length > 0 ? selectedItems : undefined,
      operationEntity,
      operationStatus,
      actor: actor || undefined,
      dateRange: dateRange.startDate || dateRange.endDate ? dateRange : undefined,
      ownerType: operationEntity === "Authorization" ? ownerType || undefined : undefined,
      ownerId: operationEntity === "Authorization" ? ownerId || undefined : undefined,
    });
  };

  const handleOperationEntityChange = (data: { selectedItem?: string | null }) => {
    const newEntity = data.selectedItem || "";
    setOperationEntity(newEntity);
    if (newEntity !== "Authorization") {
      setOwnerType("");
      setOwnerId("");
    }
    onFilterChange({
      operationType,
      operationEntity: newEntity || undefined,
      operationStatus,
      actor: actor || undefined,
      dateRange: dateRange.startDate || dateRange.endDate ? dateRange : undefined,
      ownerType: newEntity === "Authorization" ? ownerType || undefined : undefined,
      ownerId: newEntity === "Authorization" ? ownerId || undefined : undefined,
    });
  };

  const handleOperationStatusChange = (selectedItems: string[]) => {
    setOperationStatus(selectedItems);
    onFilterChange({
      operationType,
      operationEntity,
      operationStatus: selectedItems.length > 0 ? selectedItems : undefined,
      actor: actor || undefined,
      dateRange: dateRange.startDate || dateRange.endDate ? dateRange : undefined,
      ownerType: operationEntity === "Authorization" ? ownerType || undefined : undefined,
      ownerId: operationEntity === "Authorization" ? ownerId || undefined : undefined,
    });
  };

  const handleActorChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const value = event.target.value;
    setActor(value);
    onFilterChange({
      operationType,
      operationEntity,
      operationStatus,
      actor: value || undefined,
      dateRange: dateRange.startDate || dateRange.endDate ? dateRange : undefined,
      ownerType: operationEntity === "Authorization" ? ownerType || undefined : undefined,
      ownerId: operationEntity === "Authorization" ? ownerId || undefined : undefined,
    });
  };

  const handleDateRangeApply = (fromDateTime: Date, toDateTime: Date) => {
    const newDateRange = {
      startDate: fromDateTime,
      endDate: toDateTime,
    };
    setDateRange(newDateRange);
    onFilterChange({
      operationType,
      operationEntity,
      operationStatus,
      actor: actor || undefined,
      dateRange: newDateRange.startDate || newDateRange.endDate ? newDateRange : undefined,
      ownerType: operationEntity === "Authorization" ? ownerType || undefined : undefined,
      ownerId: operationEntity === "Authorization" ? ownerId || undefined : undefined,
    });
  };

  const handleOwnerTypeChange = (data: { selectedItem?: string | null }) => {
    const newOwnerType = data.selectedItem || "";
    setOwnerType(newOwnerType);
    onFilterChange({
      operationType,
      operationEntity,
      operationStatus,
      actor: actor || undefined,
      dateRange: dateRange.startDate || dateRange.endDate ? dateRange : undefined,
      ownerType: newOwnerType || undefined,
      ownerId: operationEntity === "Authorization" ? ownerId || undefined : undefined,
    });
  };

  const handleOwnerIdChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const value = event.target.value;
    setOwnerId(value);
    onFilterChange({
      operationType,
      operationEntity,
      operationStatus,
      actor: actor || undefined,
      dateRange: dateRange.startDate || dateRange.endDate ? dateRange : undefined,
      ownerType: operationEntity === "Authorization" ? ownerType || undefined : undefined,
      ownerId: value || undefined,
    });
  };

  return (
    <FilterPanelContainer>
      <FilterTitle>{t("Filter")}</FilterTitle>
      
      <FilterSection>
        <MultiSelect
          id="operation-type"
          titleText={t("Operation type")}
          label="Choose option(s)"
          items={operationTypeOptions}
          selectedItems={operationType}
          itemToString={(item) => item}
          onChange={({ selectedItems }) =>
            handleOperationTypeChange(selectedItems || [])
          }
          size="md"
          useTitleInItem={false}
        />
      </FilterSection>

      <FilterSection>
        <Dropdown
          id="operation-entity"
          titleText={t("Operation entity")}
          label="Choose option"
          items={operationEntityOptions}
          selectedItem={operationEntity}
          itemToString={(item) => item || ""}
          onChange={handleOperationEntityChange}
          size="md"
        />
      </FilterSection>

      {operationEntity === "Authorization" && (
        <>
          <FilterSection>
            <Dropdown
              id="owner-type"
              titleText={tComponents("ownerType") || "Owner type"}
              label="Choose option"
              items={OWNER_TYPES}
              selectedItem={ownerType}
              itemToString={(item) => item || ""}
              onChange={handleOwnerTypeChange}
              size="md"
            />
          </FilterSection>

          <FilterSection>
            <TextInput
              id="owner-id"
              labelText={tComponents("ownerId") || "Owner ID"}
              placeholder="Enter owner ID"
              value={ownerId}
              onChange={handleOwnerIdChange}
              size="md"
            />
          </FilterSection>
        </>
      )}

      <FilterSection>
        <MultiSelect
          id="operation-status"
          titleText={t("Operation status")}
          label="Choose option(s)"
          items={operationStatusOptions}
          selectedItems={operationStatus}
          itemToString={(item) => item}
          onChange={({ selectedItems }) =>
            handleOperationStatusChange(selectedItems || [])
          }
          size="md"
          useTitleInItem={false}
        />
      </FilterSection>

      <FilterSection>
        <TextInput
          id="actor"
          labelText={t("actor")}
          placeholder="Username or client ID"
          value={actor}
          onChange={handleActorChange}
          size="md"
        />
      </FilterSection>

      <FilterSection>
        <DateRangeField
          filterName="dateRange"
          popoverTitle={t("Date range")}
          label={t("Date range")}
          fromDateTime={dateRange.startDate}
          toDateTime={dateRange.endDate}
          onApply={handleDateRangeApply}
          isModalOpen={isDateRangeModalOpen}
          onModalClose={() => setIsDateRangeModalOpen(false)}
          onClick={() => setIsDateRangeModalOpen(true)}
        />
      </FilterSection>

      {hasActiveFilters && (
        <FilterSection>
          <ResetButtonWrapper>
            <Button
              kind="ghost"
              size="md"
              onClick={handleResetFilters}
            >
              {"Reset filter"}
            </Button>
          </ResetButtonWrapper>
        </FilterSection>
      )}
    </FilterPanelContainer>
  );
};

export default FilterPanel;

