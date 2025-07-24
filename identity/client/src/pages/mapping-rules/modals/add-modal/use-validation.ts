/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useState } from "react";

interface UseValidationProps {
  mappingRuleId: string;
  mappingRuleName: string;
  claimName: string;
  claimValue: string;
}

export const useValidation = ({
  mappingRuleId,
  mappingRuleName,
  claimName,
  claimValue,
}: UseValidationProps) => {
  const [isMappingRuleIdValid, setIsMappingRuleIdValid] =
    useState<boolean>(true);
  const [isMappingRuleNameValid, setIsMappingRuleNameValid] =
    useState<boolean>(true);
  const [isClaimNameValid, setIsClaimNameValid] = useState<boolean>(true);
  const [isClaimValueValid, setIsClaimValueValid] = useState<boolean>(true);

  const validateMappingRuleId = (value: string) =>
    validateField(value, setIsMappingRuleIdValid);

  const validateMappingRuleName = (value: string) =>
    validateField(value, setIsMappingRuleNameValid);

  const validateClaimName = (value: string) =>
    validateField(value, setIsClaimNameValid);

  const validateClaimValue = (value: string) =>
    validateField(value, setIsClaimValueValid);

  const validateForm = () => {
    const _isMappingRuleIdValid = validateMappingRuleId(mappingRuleId);
    const _isMappingRuleNameValid = validateMappingRuleName(mappingRuleName);
    const _isClaimNameValid = validateClaimName(claimName);
    const _isClaimValueValid = validateClaimValue(claimValue);
    return (
      _isMappingRuleIdValid &&
      _isMappingRuleNameValid &&
      _isClaimNameValid &&
      _isClaimValueValid
    );
  };

  return {
    isMappingRuleIdValid,
    isMappingRuleNameValid,
    isClaimNameValid,
    isClaimValueValid,
    validateMappingRuleId,
    validateMappingRuleName,
    validateClaimName,
    validateClaimValue,
    validateForm,
  };
};

const isNonEmpty = (value: string) => value.trim() !== "";

const validateField = (
  value: string,
  setValidity: (isValid: boolean) => void,
) => {
  const valid = isNonEmpty(value);
  setValidity(valid);
  return valid;
};
