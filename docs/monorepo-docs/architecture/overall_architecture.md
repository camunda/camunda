---
id: overall_architecture
title: Overall Architecture
sidebar_position: 1
toc_min_heading_level: 2
toc_max_heading_level: 4
---

# 1. Introduction and goals

WIP!!!

This document describes the overall Camunda 8 architecture (Hub and Orchestration Clusters) as of **Camunda 8.9**, before any future “unified identity” consolidation between Hub and runtime.

Camunda provides a cloud-native orchestration platform composed of:

- Camunda Hub: central entry point for account, tenant, and configuration management, as well as modeling and governance features.
- Orchestration Clusters: runtime environments for executing process workloads.

The goal of this document is to:

- Provide a shared, high-level mental model of the complete Camunda system (Hub and Orchestration Clusters).
- Distinguish clearly between SaaS and Self-Managed deployments.
- Serve as a starting point for more detailed arc42 sections (constraints, runtime views, etc.).

---

# 3. System context and scope

## 3.2 Technical context – Camunda SaaS

```mermaid
---
title: Camunda SaaS - Technical Context
---
flowchart TB

  USER["User (Browser)"]
  EXT_SYS["Customer Systems\n(Workers, backends, etc.)"]

  CAMUNDA_SAAS["Camunda 8 SaaS"]

  AUTH0[("Auth0\n(Camunda SaaS IdP)")]
  PRIMARY_DB[("Primary Database (RocksDB)")]
  SECONDARY_DB[("Secondary Database\n(Elasticsearch / OpenSearch)")]
  MGMT_DB[("Management Databases\n(PostgreSQL for Accounts / Console)")]
  CLOUD_INFRA[("Cloud Infrastructure\n(GCP / AWS managed services)")]

  USER --> CAMUNDA_SAAS
  EXT_SYS --> CAMUNDA_SAAS

  CAMUNDA_SAAS --> AUTH0
  CAMUNDA_SAAS --> PRIMARY_DB
  CAMUNDA_SAAS --> SECONDARY_DB
  CAMUNDA_SAAS --> MGMT_DB
  CAMUNDA_SAAS --> CLOUD_INFRA
```

Key aspects:

- Camunda 8 SaaS (Hub and Orchestration Clusters) is fully operated and hosted by Camunda; customers access all UIs via a browser and all APIs via the public internet.
- Identity for Hub and runtime UIs/APIs is provided by Auth0 together with the internal Accounts Service; there is no separate Management Identity in SaaS.
- Persistence, search, and management data stores (primary RocksDB, secondary Elasticsearch/OpenSearch, management PostgreSQL) as well as cloud infrastructure services (GCP/AWS) are internal dependencies of the Camunda 8 SaaS black box and managed by Camunda.
- External workers and systems interact with the Orchestration Cluster APIs that are part of the Camunda 8 SaaS black box; Hub APIs are primarily used for account/organization/tenant and configuration concerns, not for direct process execution.

## 3.3 Technical context – Camunda Self-Managed

```mermaid
---
title: Camunda Self-Managed - Technical Context
---
flowchart TB

  USER["User (Browser)"]

  subgraph CUSTOMER_INFRA["Customer Infrastructure"]
    EXT_SYS["Customer Systems\n(Workers, backends, etc.)"]
    IDP[("Customer IdP")]
    CUSTOMER_DB[("Customer Datastores\n(DB, search, RDBMS, etc.)")]
    CAMUNDA_SM["Camunda 8 Self-Managed"]
  end

  USER --> CAMUNDA_SM
  EXT_SYS --> CAMUNDA_SM
  CAMUNDA_SM --> IDP
  CAMUNDA_SM --> CUSTOMER_DB
```

Key aspects:

- Camunda 8 Self-Managed (Hub and Orchestration Clusters) is deployed and operated entirely inside the customer's infrastructure and network.
- From a technical context perspective, Camunda 8 Self-Managed behaves as a single black box application inside customer infrastructure: customer systems and workers call its APIs, and it in turn relies on customer-provided infrastructure services and IdP.
- The customer is responsible for provisioning and operating all required infrastructure services around Camunda 8 (databases, search, observability, ingress, networking, backups, etc.).

---

# 5. Building block view

## 5.1 Whitebox overall system – Camunda SaaS

This section provides a whitebox view of the main logical building blocks in Camunda SaaS, their responsibilities, and high-level interactions.

```mermaid
---
title: Camunda SaaS - Whitebox Overall System
---
flowchart TB

  subgraph CLIENTS["Clients"]
    USER["Users (Browser)"]
    WORKER["Workers / Backend Services"]
  end

  AUTH0[("Auth0\n(Camunda SaaS IdP)")]

  subgraph CAMUNDA_SAAS["Camunda 8 SaaS"]

    subgraph HUB_FE["Hub Frontend Apps"]
      FE_CONSOLE["Console UI"]
      FE_WEB_MODELER["Web Modeler"]
    end

    subgraph HUB_BE["Hub Backend Services"]
      SVC_ACCOUNTS["Accounts Service"]
      SVC_ORG["Organizations / Projects / Tenants"]
      SVC_PROVISIONING["Provisioning / Control Plane"]
      SVC_HUB_API["Hub API / BFF"]
    end

    subgraph OC_PUBLIC["Orchestration Clusters"]
      OC_UIS["Cluster UIs\n(Operate, Tasklist)"]
      OC_APIS["Cluster APIs\n(Gateway REST/gRPC)"]
    end

    subgraph OPTIMIZE_SAAS["Optimize"]
      OPTIMIZE_UI["Optimize UI"]
      OPTIMIZE_SVC["Optimize Service"]
      OPTIMIZE_DB[("Optimize DB")]
    end
  end

  USER --> FE_CONSOLE
  USER --> FE_WEB_MODELER
  USER --> OC_UIS
  USER --> OPTIMIZE_UI

  WORKER --> OC_APIS
  OC_APIS --> WORKER

  FE_CONSOLE --> SVC_HUB_API
  FE_WEB_MODELER --> SVC_HUB_API

  SVC_HUB_API --> SVC_ACCOUNTS
  SVC_HUB_API --> SVC_ORG
  SVC_HUB_API --> SVC_PROVISIONING

  HUB_BE --> OC_PUBLIC

  OPTIMIZE_UI --> OPTIMIZE_SVC
  OPTIMIZE_SVC --> OPTIMIZE_DB

  CAMUNDA_SAAS --> AUTH0
  AUTH0 --> CAMUNDA_SAAS
```

Main building blocks (SaaS):

- Hub frontend apps: Console UI and Web Modeler as browser-based entry points for account, tenant, environment, and modeling/governance features.
- Hub backend services: Accounts, organization/project/tenant configuration, provisioning/control plane, and Hub API/BFF; these services back the Hub frontends and persist Hub-level state in management databases.
- Orchestration Clusters (public surface): cluster UIs (Operate, Tasklist) and cluster APIs (gateway REST/gRPC) used by users and workers; internal details of the engine and persistence are intentionally not shown in this whitebox.
- Orchestration Cluster runtime internals (conceptual, not shown in the diagram): Zeebe engine, cluster identity, and primary/secondary datastores providing execution, IAM enforcement, and search/query capabilities.
- Optimize: analytics and reporting application with its own UI, backend service, and dedicated Optimize DB; not part of Hub, but integrated into Camunda 8 SaaS and reusing the same organizations/tenants and identity setup.
- Auth0 (Camunda SaaS IdP): SaaS identity provider used for Hub, runtime, and Optimize authentication, integrated with Accounts and Cluster Identity.

## 5.2 Whitebox overall system – Camunda Self-Managed

```mermaid
---
title: Camunda Self-Managed - Whitebox Overall System
---
flowchart TB

  subgraph CLIENTS_SM["Clients"]
    USER_SM["Users (Browser)"]
    WORKER_SM["Workers / Backend Services"]
  end

  IDP_SM[("Enterprise IdP")]

  subgraph CUSTOMER_STACK["Customer Camunda Stack"]

    subgraph CAMUNDA_SM["Camunda 8 Self-Managed"]

      subgraph HUB_FE_SM["Hub Frontend Apps"]
        FE_CONSOLE_SM["Console UI"]
        FE_WEB_MODELER_SM["Web Modeler"]
      end

      subgraph MGMT_ID_SM["Management Identity"]
        MGMT_ID_APP_SM["Management Identity App"]
      end

      subgraph HUB_BE_SM["Hub Backend Services"]
        SVC_ACCOUNTS_SM["Accounts Service"]
        SVC_ORG_SM["Organizations / Projects / Tenants"]
        SVC_PROVISIONING_SM["Provisioning / Control Plane"]
        SVC_HUB_API_SM["Hub API / BFF"]
      end

      subgraph OC_PUBLIC_SM["Orchestration Clusters"]
        OC_UIS_SM["Cluster UIs\n(Operate, Tasklist)"]
        OC_APIS_SM["Cluster APIs\n(Gateway REST/gRPC)"]
      end

      subgraph OPTIMIZE_SM["Optimize"]
        OPTIMIZE_UI_SM["Optimize UI"]
        OPTIMIZE_SVC_SM["Optimize Service"]
        OPTIMIZE_DB_SM[("Optimize DB")]
      end
    end
  end

  USER_SM --> FE_CONSOLE_SM
  USER_SM --> FE_WEB_MODELER_SM
  USER_SM --> OC_UIS_SM
  USER_SM --> OPTIMIZE_UI_SM

  WORKER_SM --> OC_APIS_SM
  OC_APIS_SM --> WORKER_SM

  FE_CONSOLE_SM --> SVC_HUB_API_SM
  FE_WEB_MODELER_SM --> SVC_HUB_API_SM

  SVC_HUB_API_SM --> SVC_ACCOUNTS_SM
  SVC_HUB_API_SM --> SVC_ORG_SM
  SVC_HUB_API_SM --> SVC_PROVISIONING_SM

  HUB_BE_SM --> OC_PUBLIC_SM

  OPTIMIZE_UI_SM --> OPTIMIZE_SVC_SM
  OPTIMIZE_SVC_SM --> OPTIMIZE_DB_SM

  HUB_FE_SM --> MGMT_ID_APP_SM
  OC_UIS_SM --> MGMT_ID_APP_SM
  OPTIMIZE_UI_SM --> MGMT_ID_APP_SM

  MGMT_ID_APP_SM --> IDP_SM
  OC_APIS_SM --> IDP_SM
  IDP_SM --> MGMT_ID_APP_SM
```

Main building blocks (Self-Managed):

- Hub frontend apps (Self-Managed): Console UI and Web Modeler with the same logical responsibilities as in SaaS, but deployed and operated by the customer.
- Hub backend services (Self-Managed): Accounts, organization/project/tenant configuration, provisioning/control plane, and Hub API/BFF; same logical responsibilities as in SaaS, but talking to customer-managed datastores.
- Management Identity (Self-Managed): Hub-level IAM for Console, Web Modeler, and Optimize; integrates with the customer’s Enterprise IdP and issues tokens consumed by these apps.
- Orchestration Clusters (public surface): cluster UIs (Operate, Tasklist) and cluster APIs (gateway REST/gRPC) used by users and workers; engine, storage, and other internals are not shown in this whitebox, but mirror the SaaS responsibilities.
- Orchestration Cluster runtime internals (conceptual, not shown in the diagram): Zeebe engine, cluster identity, and primary/secondary datastores running in customer infrastructure and managed by the customer.
- Optimize (Self-Managed): separate application with its own UI, backend service, and dedicated Optimize DB; integrates with Hub concepts (organizations, tenants) and reuses Management Identity and the Enterprise IdP for authentication.
- Enterprise IdP: customer-provided IdP for SSO and JWTs; integrates with Management Identity (for Hub and Optimize) and Cluster Identity (for runtime UIs/APIs).
- Observability stack (conceptual, not shown): customer-provided logging, metrics, and tracing for Hub, Optimize, and clusters.

---

# 11. Glossary (initial, high-level)

- Camunda Hub: Central entry point for managing organizations, tenants, projects, clusters, and modeling/governance capabilities.
- Orchestration Cluster: Runtime deployment containing the workflow engine, cluster UIs, cluster identity, and runtime APIs.
- Management Identity (Self-Managed): Standalone identity app for Hub components such as Web Modeler, Console, and Optimize in Self-Managed environments.
- Cluster Identity: Identity and access management embedded in an Orchestration Cluster, enforcing runtime access for cluster UIs and APIs.
- Auth0 (Camunda SaaS IdP): Hosted identity provider used by Camunda 8 SaaS for Hub, runtime, and Optimize authentication.
- Enterprise IdP: External identity provider (e.g., Entra ID, Okta, Keycloak) integrating with Self-Managed Hub, Optimize, and Cluster Identity for SSO and token-based access.
- Worker / Automation: Non-interactive client calling Camunda APIs or processing work items.
