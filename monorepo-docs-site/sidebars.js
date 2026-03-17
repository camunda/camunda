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
    'collaboration-guidelines',
    'ci',
    'ci-runbooks',
    'infrastructure-services',
    'renovate-pr-handling',
    'release',
    {
      type: 'category',
      label: 'Architecture',
      items: [
        'architecture/architecture_overview',
        {
          type: 'category',
          label: 'Components',
          items: [
            {
              type: 'category',
              label: 'Identity',
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
                  label: 'Misc',
                  items: [
                    'architecture/components/identity/misc/data-model',
                    'architecture/components/identity/misc/default-roles',
                    'architecture/components/identity/misc/rp-initiated-logout',
                  ],
                },
              ],
            },
          ],
        },
      ],
    },
  ],
};

module.exports = sidebars;
