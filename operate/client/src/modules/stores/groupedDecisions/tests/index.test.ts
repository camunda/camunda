/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {waitFor} from 'modules/testing-library';
import {groupedDecisions} from 'modules/mocks/groupedDecisions';
import {generateDecisionKey, groupedDecisionsStore} from '../';
import {mockFetchGroupedDecisions} from 'modules/mocks/api/decisions/fetchGroupedDecisions';

describe('groupedDecisionsStore', () => {
  afterEach(() => {
    groupedDecisionsStore.reset();
  });

  beforeEach(async () => {
    mockFetchGroupedDecisions().withSuccess(groupedDecisions);

    expect(groupedDecisionsStore.state.status).toBe('initial');

    groupedDecisionsStore.fetchDecisions();

    await waitFor(() => {
      expect(groupedDecisionsStore.state.status).toBe('fetched');
    });
  });

  it('should fetch grouped decisions', () => {
    expect(
      groupedDecisionsStore.state.decisions.map((decision) => {
        const {key, ...decisionDto} = decision;
        return decisionDto;
      }),
    ).toEqual(groupedDecisions);
    expect(groupedDecisionsStore.areDecisionsEmpty).toBe(false);
  });

  it('should get decision definition id', () => {
    expect(
      groupedDecisionsStore.getDecisionDefinitionId({
        decisionId: 'invoice-assign-approver',
        version: 1,
      }),
    ).toEqual('0');
  });

  it('should get decision name', () => {
    const {getDecisionName} = groupedDecisionsStore;

    expect(getDecisionName({decisionId: 'invoice-assign-approver'})).toBe(
      'Assign Approver Group',
    );
    expect(
      getDecisionName({
        decisionId: 'invoice-assign-approver',
        tenantId: '<default>',
      }),
    ).toBe('Assign Approver Group');
    expect(
      getDecisionName({
        decisionId: 'invoice-assign-approver',
        tenantId: 'tenant-A',
      }),
    ).toBe('Assign Approver Group for tenant A');
    expect(getDecisionName({decisionId: 'invoiceClassification'})).toBe(
      'invoiceClassification',
    );
    expect(getDecisionName({decisionId: 'calc-key-figures'})).toBe(
      'Calculate Credit History Key Figures',
    );

    expect(
      getDecisionName({
        decisionId: 'calc-key-figures',
        tenantId: 'invalid-tenant-id',
      }),
    ).toBeUndefined();
    expect(getDecisionName({decisionId: 'invalidId'})).toBeUndefined();
    expect(getDecisionName({decisionId: null})).toBeUndefined();
  });

  it('should return null for invalid decision ids', () => {
    expect(
      groupedDecisionsStore.getDecisionDefinitionId({
        decisionId: 'invalidDecisionId',
        version: 1,
      }),
    ).toEqual(null);
  });

  it('should get sorted decision options', () => {
    expect(groupedDecisionsStore.decisions).toEqual([
      {
        label: 'Assign Approver Group',
        decisionId: 'invoice-assign-approver',
        id: '{invoice-assign-approver}-{<default>}',
        tenantId: '<default>',
      },
      {
        label: 'Assign Approver Group for tenant A',
        decisionId: 'invoice-assign-approver',
        id: '{invoice-assign-approver}-{tenant-A}',
        tenantId: 'tenant-A',
      },
      {
        label: 'Calculate Credit History Key Figures',
        decisionId: 'calc-key-figures',
        id: '{calc-key-figures}-{<default>}',
        tenantId: '<default>',
      },
      {
        label: 'invoiceClassification',
        decisionId: 'invoiceClassification',
        id: '{invoiceClassification}-{<default>}',
        tenantId: '<default>',
      },
    ]);
  });

  it('should get decision versions by key', () => {
    const firstDecision = groupedDecisions[0]!;
    const secondDecision = groupedDecisions[1]!;
    const thirdDecision = groupedDecisions[2]!;
    const fourthDecision = groupedDecisions[3]!;

    const [version2, version1] = firstDecision.decisions;
    const [version5, version4, version3] = secondDecision.decisions;

    expect(
      groupedDecisionsStore.decisionVersionsByKey[
        generateDecisionKey('invoice-assign-approver', '<default>')
      ],
    ).toEqual([version1, version2]);
    expect(
      groupedDecisionsStore.decisionVersionsByKey[
        generateDecisionKey('invoice-assign-approver', 'tenant-A')
      ],
    ).toEqual([version3, version4, version5]);
    expect(
      groupedDecisionsStore.decisionVersionsByKey[
        generateDecisionKey('invoiceClassification')
      ],
    ).toEqual(thirdDecision.decisions);
    expect(
      groupedDecisionsStore.decisionVersionsByKey[
        generateDecisionKey('calc-key-figures')
      ],
    ).toEqual(fourthDecision.decisions);
  });

  it('should get versions', () => {
    expect(
      groupedDecisionsStore.getVersions(
        generateDecisionKey('invoiceClassification'),
      ),
    ).toEqual([1]);
    expect(
      groupedDecisionsStore.getVersions(
        generateDecisionKey('invoice-assign-approver'),
      ),
    ).toEqual([1, 2]);
    expect(
      groupedDecisionsStore.getVersions(
        generateDecisionKey('invoice-assign-approver', 'tenant-A'),
      ),
    ).toEqual([1, 2, 3]);
    expect(
      groupedDecisionsStore.getVersions(
        generateDecisionKey('invalidDecisionId'),
      ),
    ).toEqual([]);
  });

  it('should get default version', () => {
    expect(
      groupedDecisionsStore.getDefaultVersion(
        generateDecisionKey('invoiceClassification'),
      ),
    ).toBe(1);
    expect(
      groupedDecisionsStore.getDefaultVersion(
        generateDecisionKey('invoice-assign-approver'),
      ),
    ).toBe(2);
    expect(
      groupedDecisionsStore.getDefaultVersion(
        generateDecisionKey('invoice-assign-approver', 'tenant-A'),
      ),
    ).toBe(3);
    expect(
      groupedDecisionsStore.getDefaultVersion(
        generateDecisionKey('invalidDecisionId'),
      ),
    ).toBeUndefined();
  });

  it('should get decision', () => {
    expect(
      groupedDecisionsStore.getDecision('invoice-assign-approver')?.key,
    ).toBe('{invoice-assign-approver}-{<default>}');
    expect(
      groupedDecisionsStore.getDecision('invoice-assign-approver', '<default>')
        ?.key,
    ).toBe('{invoice-assign-approver}-{<default>}');
    expect(
      groupedDecisionsStore.getDecision('invoice-assign-approver', 'tenant-A')
        ?.key,
    ).toBe('{invoice-assign-approver}-{tenant-A}');
    expect(
      groupedDecisionsStore.getDecision('invoiceClassification')?.key,
    ).toBe('{invoiceClassification}-{<default>}');
    expect(
      groupedDecisionsStore.getDecision('calc-key-figures', '<default>')?.key,
    ).toBe('{calc-key-figures}-{<default>}');
  });
});
