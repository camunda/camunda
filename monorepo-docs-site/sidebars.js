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
    'monorepo-devops-team-processes',
    'collaboration-guidelines',
    'ci',
    'ci-runbooks',
    'dependency-vulnerability-gate',
    'infrastructure-services',
    'processes',
    'release',
    {
      type: 'category',
      label: 'Architecture',
      items: [
        {
          type: 'category',
          label: 'Components',
          items: [
            {
              type: 'category',
              label: 'RDBMS',
              items: [
                'architecture/components/rdbms/rdbms_architecture_docs',
                'architecture/components/rdbms/developer-guide',
                'architecture/components/rdbms/benchmarking',
                {
                  type: 'category',
                  label: 'ADRs',
                  items: [
                    'architecture/components/rdbms/adr/use-mybatis-as-orm-framework',
                    'architecture/components/rdbms/adr/use-liquibase-for-schema-management',
                  ],
                },
              ],
            },
            {
              type: 'category',
              label: 'Orchestration Cluster Identity',
              items: [
                'architecture/components/identity/identity_architecture_docs',
                {
                  type: 'category',
                  label: 'ADRs',
                  items: [
                    'architecture/components/identity/adr/cluster-embedded-identity',
                    'architecture/components/identity/adr/oidc-default-production-authentication',
                    'architecture/components/identity/adr/resource-based-authorization-model',
                    'architecture/components/identity/adr/multi-jwks-endpoints-per-issuer',
                    'architecture/components/identity/adr/support-forward-slashes-in-entity-ids',
                  ],
                },
                {
                  type: 'category',
                  label: 'Authorizations',
                  items: [
                    'architecture/components/identity/authorizations/authorization-concept',
                    'architecture/components/identity/authorizations/engine-authorization',
                    'architecture/components/identity/authorizations/rest-authorization',
                  ],
                },
                {
                  type: 'category',
                  label: 'References',
                  items: [
                    'architecture/components/identity/references/data-model',
                    'architecture/components/identity/references/default-roles',
                    'architecture/components/identity/references/rp-initiated-logout',
                  ],
                },
              ],
            },
            {
              type: 'category',
              label: 'Orchestration Cluster',
              items: [
                {
                  type: 'category',
                  label: 'ADRs',
                  items: [
                    'architecture/components/orchestration-cluster/adr/jvm-options-argfile-for-runtime-specific-flags',
                    'architecture/components/orchestration-cluster/adr/jdk-25-base-images-with-jdk-21-runtime-support',
                  ],
                },
              ],
            },
            {
              type: 'doc',
              id: 'architecture/components/identity/management_identity_architecture_docs',
              label: 'Management Identity',
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
