/**
 * Creating a sidebar enables you to:
 - create an ordered group of docs
 - render a sidebar for each doc of that group
 - provide next/previous navigation

 The sidebars can be generated from the filesystem, or explicitly defined here.

 Create as many sidebars as you want.
 */

// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  tutorialSidebar: [
    'index',
    'eng-ops-processes',
    'collaboration-guidelines',
    {
      type: 'category',
      label: 'CI',
      link: { type: 'doc', id: 'ci' },
      items: ['ci-runbooks', 'flaky-test-gate', 'dependency-vulnerability-gate'],
    },
    'infrastructure-services',
    'processes',
    {
      type: 'category',
      label: 'Release Process',
      link: { type: 'doc', id: 'release/index' },
      items: ['release/release-monorepo', 'release/release-train'],
    },
    {
      type: 'category',
      label: 'Architecture',
      items: [
        {
          type: 'category',
          label: 'Cross-cutting Architecture',
          items: [
            {
              type: 'link',
              label: 'Overview',
              href: '/architecture/overview',
            },
            {
              type: 'link',
              label: 'Orchestration Cluster',
              href: '/adr/orchestration-cluster/jvm-options-argfile-for-runtime-specific-flags',
            },
            {
              type: 'link',
              label: 'Management',
              href: '/adr/management/physical-tenant-health-status-topology',
            },
            {
              type: 'link',
              label: 'Security',
              href: '/adr/security/endpoint-required-permission-mapping',
            },
            {
              type: 'link',
              label: 'Clients',
              href: '/adr/clients/unify-spring-starter-on-multi-client-config-path',
            },
          ],
        },
        {
          type: 'category',
          label: 'Components',
          items: [
            {
              type: 'category',
              label: 'Orchestration Cluster Identity',
              items: [
                {
                  type: 'link',
                  label: 'Architecture',
                  href: '/identity/architecture',
                },
                {
                  type: 'category',
                  label: 'Authorizations',
                  items: [
                    { type: 'link', label: 'Authorization Concept', href: '/identity/authorizations/authorization-concept' },
                    { type: 'link', label: 'Engine Authorization', href: '/identity/authorizations/engine-authorization' },
                    { type: 'link', label: 'REST Authorization', href: '/identity/authorizations/rest-authorization' },
                  ],
                },
                {
                  type: 'category',
                  label: 'References',
                  items: [
                    { type: 'link', label: 'Data Model', href: '/identity/references/data-model' },
                    { type: 'link', label: 'Default Roles', href: '/identity/references/default-roles' },
                    { type: 'link', label: 'RP-Initiated Logout', href: '/identity/references/rp-initiated-logout' },
                  ],
                },
                {
                  type: 'category',
                  label: 'ADRs',
                  items: [
                    { type: 'link', label: 'ADR Index', href: '/identity/adr/README' },
                  ],
                },
              ],
            },
            {
              type: 'link',
              label: 'Management Identity',
              href: '/identity/management-identity',
            },
          ],
        },
      ],
    },
    {
      type: 'category',
      label: 'Frontend',
      link: { type: 'doc', id: 'frontend/frontend' },
      items: [
        'frontend/getting-started',
        'frontend/project-outline',
        'frontend/orchestration-cluster-webapp',
        'frontend/camunda-api-zod-schemas',
        'frontend/data-loading',
        'frontend/forms',
        {
          type: 'category',
          label: 'Development process',
          link: {
            type: 'doc',
            id: 'frontend/development-process/development-process',
          },
          items: [
            'frontend/development-process/before-starting',
            'frontend/development-process/creating-a-new-page',
            'frontend/development-process/extending-an-existing-page',
            'frontend/development-process/working-on-large-feature',
            'frontend/development-process/generating-svg-components',
          ],
        },
        'frontend/testing',
        'frontend/using-ai',
        'frontend/code-reviews',
        {
          type: 'category',
          label: 'ADRs',
          link: { type: 'doc', id: 'frontend/adr/adr' },
          items: [],
        },
        'frontend/code-style',
        'frontend/legacy-components',
      ],
    },
  ],
};

module.exports = sidebars;
