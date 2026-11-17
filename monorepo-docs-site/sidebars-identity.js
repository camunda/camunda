// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  identitySidebar: [
    'architecture',
    'management-identity',
    {
      type: 'category',
      label: 'Authorizations',
      items: [
        'authorizations/authorization-concept',
        'authorizations/engine-authorization',
        'authorizations/rest-authorization',
      ],
    },
    {
      type: 'category',
      label: 'References',
      items: [
        'references/data-model',
        'references/default-roles',
        'references/rp-initiated-logout',
      ],
    },
    {
      type: 'category',
      label: 'ADRs',
      link: { type: 'doc', id: 'adr/README' },
      items: [
        'adr/cluster-embedded-identity',
        'adr/oidc-default-production-authentication',
        'adr/resource-based-authorization-model',
        'adr/multi-jwks-endpoints-per-issuer',
        'adr/support-forward-slashes-in-entity-ids',
        'adr/userinfo-claim-augmentation-for-bearer-tokens',
      ],
    },
  ],
};

module.exports = sidebars;
