/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
import {fetchLicense, License} from 'modules/api/v2/fetchLicense';
import {logger} from 'modules/logger';

type State = {
  status: 'initial' | 'fetched' | 'error';
  isProductionLicense: boolean;
  isTagVisible: boolean;
  isCommercial: boolean;
  expiresAt?: string;
};

const DEFAULT_STATE: State = {
  status: 'initial',
  isProductionLicense: false,
  isTagVisible: false,
  isCommercial: false,
};

class LicenseTag {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  fetchLicense = async () => {
    const response = await fetchLicense();

    if (response.isSuccess) {
      this.handleFetchSuccess(response.data);
    } else {
      this.handleFetchFailure();
    }
  };

  handleFetchSuccess = ({
    validLicense,
    licenseType,
    isCommercial,
    expiresAt,
  }: License) => {
    this.state.status = 'fetched';
    this.state.isProductionLicense = validLicense;
    this.state.isTagVisible = licenseType !== 'saas';
    this.state.isCommercial = isCommercial;
    this.state.expiresAt = expiresAt ?? undefined;
  };

  handleFetchFailure = () => {
    this.state.status = 'error';
    logger.error(`Failed to fetch license`);
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const licenseTagStore = new LicenseTag();
