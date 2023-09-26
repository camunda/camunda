/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
        value: 'invoice-assign-approver',
      },
      {
        label: 'Assign Approver Group for tenant A',
        value: 'invoice-assign-approver',
      },
      {
        label: 'Calculate Credit History Key Figures',
        value: 'calc-key-figures',
      },
      {
        label: 'invoiceClassification',
        value: 'invoiceClassification',
      },
    ]);
  });

  it('should get decision versions by id', () => {
    const firstDecision = groupedDecisions[0]!;
    const thirdDecision = groupedDecisions[2]!;
    const fourthDecision = groupedDecisions[3]!;

    const [version2, version1] = firstDecision.decisions;

    expect(
      groupedDecisionsStore.decisionVersionsById['invoice-assign-approver'],
    ).toEqual([version1, version2]);
    expect(
      groupedDecisionsStore.decisionVersionsById['invoiceClassification'],
    ).toEqual(thirdDecision.decisions);
    expect(
      groupedDecisionsStore.decisionVersionsById['calc-key-figures'],
    ).toEqual(fourthDecision.decisions);
  });

  it('should get decision versions by key', () => {
    const firstDecision = groupedDecisions[0]!;
    const secondDecision = groupedDecisions[1]!;
    const thirdDecision = groupedDecisions[2]!;
    const fourthDecision = groupedDecisions[3]!;

    const [version2, version1] = firstDecision.decisions;
    const [version4, version3] = secondDecision.decisions;

    expect(
      groupedDecisionsStore.decisionVersionsByKey[
        generateDecisionKey('invoice-assign-approver', '<default>')
      ],
    ).toEqual([version1, version2]);
    expect(
      groupedDecisionsStore.decisionVersionsByKey[
        generateDecisionKey('invoice-assign-approver', 'tenant-A')
      ],
    ).toEqual([version3, version4]);
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
    expect(groupedDecisionsStore.getVersions('invoiceClassification')).toEqual([
      1,
    ]);
    expect(
      groupedDecisionsStore.getVersions('invoice-assign-approver'),
    ).toEqual([1, 2]);
    expect(groupedDecisionsStore.getVersions('invalidDecisionId')).toEqual([]);
  });

  it('should get default version', () => {
    expect(
      groupedDecisionsStore.getDefaultVersion('invoiceClassification'),
    ).toBe(1);
    expect(
      groupedDecisionsStore.getDefaultVersion('invoice-assign-approver'),
    ).toBe(2);
    expect(
      groupedDecisionsStore.getDefaultVersion('invalidDecisionId'),
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
