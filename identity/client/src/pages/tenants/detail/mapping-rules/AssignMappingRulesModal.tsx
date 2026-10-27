/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useState } from "react";
import { UseEntityModalCustomProps } from "src/components/modal";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { tenantMutations } from "src/utility/api/tenants/mutations";
import { MappingRuleMultiSelect } from "src/components/form/entitySelection/MappingRuleSelection";
import FormModal from "src/components/modal/FormModal";
import type {
  MappingRule,
  Tenant,
} from "@camunda/camunda-api-zod-schemas/8.10";

const AssignMappingRulesModal: FC<
  UseEntityModalCustomProps<
    { id: Tenant["tenantId"] },
    { assignedMappingRules: MappingRule[] }
  >
> = ({ entity: tenant, assignedMappingRules, onSuccess, open, onClose }) => {
  const { t, Translate } = useTranslate("tenants");
  const [selectedMappingRules, setSelectedMappingRules] = useState<
    MappingRule[]
  >([]);
  const [loadingAssignMappingRule, setLoadingAssignMappingRule] =
    useState(false);

  const qc = useQueryClient();
  const { mutateAsync: callAssignMappingRule } = useMutation(
    tenantMutations.assignMappingRule(qc),
  );

  const canSubmit = tenant && selectedMappingRules.length;

  const handleSubmit = async () => {
    if (!canSubmit) return;

    setLoadingAssignMappingRule(true);
    try {
      await Promise.all(
        selectedMappingRules.map(({ mappingRuleId }) =>
          callAssignMappingRule({
            mappingRuleId: mappingRuleId,
            tenantId: tenant.id,
          }),
        ),
      );
      onSuccess();
    } catch {
      // error handled globally
    } finally {
      setLoadingAssignMappingRule(false);
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
      <MappingRuleMultiSelect
        value={selectedMappingRules}
        onChange={setSelectedMappingRules}
        excluded={assignedMappingRules}
        autoFocus
      />
    </FormModal>
  );
};

export default AssignMappingRulesModal;
