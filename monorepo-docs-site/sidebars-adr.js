// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  adrSidebar: [
    {
      type: 'category',
      label: 'Orchestration Cluster',
      link: { type: 'doc', id: 'orchestration-cluster/README' },
      items: [
        'orchestration-cluster/jvm-options-argfile-for-runtime-specific-flags',
        'orchestration-cluster/jdk-25-base-images-with-jdk-21-runtime-support',
        'orchestration-cluster/physical-tenant-request-scoping-via-pre-security-filter',
        'orchestration-cluster/per-physical-tenant-provider-selection-via-assigned',
        'orchestration-cluster/physical-tenant-routing-of-authorization-reads',
        'orchestration-cluster/physical-tenant-scoped-grpc-authentication',
        'orchestration-cluster/physical-tenant-configuration-resolution-and-validation',
        'orchestration-cluster/physical-tenant-exporter-assignment-and-args-merge',
        'orchestration-cluster/propagating-physical-tenant-context-across-async-authorization-reads',
      ],
    },
    {
      type: 'category',
      label: 'Management',
      items: [
        'management/physical-tenant-health-status-topology',
        'management/management-endpoint-authorization',
        'management/physical-tenant-management-endpoint-inventory',
      ],
    },
    {
      type: 'category',
      label: 'Security',
      items: [
        'security/endpoint-required-permission-mapping',
        'security/tenant-access-provider-ownership-and-seam',
      ],
    },
    {
      type: 'category',
      label: 'Clients',
      items: [
        'clients/unify-spring-starter-on-multi-client-config-path',
      ],
    },
  ],
};

module.exports = sidebars;
