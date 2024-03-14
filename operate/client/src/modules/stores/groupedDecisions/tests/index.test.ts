/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
