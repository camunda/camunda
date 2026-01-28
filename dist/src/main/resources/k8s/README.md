# ES to RDBMS Migration Tool

This directory contains Kubernetes manifests for running the Elasticsearch to RDBMS migration as a Kubernetes Job.

## Overview

The migration tool transfers all data from Elasticsearch indices to an RDBMS database (PostgreSQL, MySQL, MariaDB, Oracle, or MS SQL Server). This enables switching Camunda's read path from Elasticsearch to RDBMS.

## Prerequisites

1. **Camunda must be running** with the actuator endpoint accessible
2. **RDBMS must be initialized** - Camunda creates the schema on first startup with RDBMS enabled
3. **Network connectivity** - The Job needs access to:
   - Elasticsearch cluster
   - RDBMS database

## Files

| File | Description |
|------|-------------|
| `es-rdbms-migration-job.yaml` | Kubernetes Job that runs the migration |
| `es-rdbms-migration-configmap.yaml` | Configuration for ES, RDBMS, actuator URLs and credentials |

## Quick Start

### 1. Prepare the RDBMS

```yaml
camunda:
  data:
    secondary-storage:
      type: rdbms
      rdbms:
        url: jdbc:postgresql://postgres:5432/camunda
        username: camunda
        password: camunda
```

### 2. Configure the Migration

Edit the ConfigMap with your environment settings:

```bash
# Edit the configmap file with your values
vi es-rdbms-migration-configmap.yaml
```

Key values to set:
- `elasticsearch.url`: Your ES cluster URL
- `elasticsearch.username`: ES username (optional, leave empty if no auth)
- `elasticsearch.password`: ES password (optional, leave empty if no auth)
- `rdbms.url`: Your RDBMS JDBC URL
- `rdbms.username`: RDBMS username
- `rdbms.password`: RDBMS password

### 3. Run the Migration

```bash
# Apply configuration
kubectl apply -f es-rdbms-migration-configmap.yaml

# Start the migration job
kubectl apply -f es-rdbms-migration-job.yaml

# Watch progress
kubectl logs -f job/es-rdbms-migration
```

## Migration Process

The migration tool performs these steps:

1. **Verify connectivity** - Checks the actuator endpoint is accessible
2. **Enable RDBMS exporter** - `POST /actuator/exporters/rdbms/enable`
3. **Soft pause exporting** - `POST /actuator/exporting/pause?soft=true` (prevents log compaction)
4. **Migrate data** in dependency order:
   - Tier 1: ProcessDefinition, DecisionRequirements, DecisionDefinition
   - Tier 2: ProcessInstance
   - Tier 3: FlowNodeInstance, Variable, SequenceFlow
   - Tier 4: UserTask, Job, MessageSubscription, DecisionInstance
   - Tier 5: Incident
5. **Resume exporting** - `POST /actuator/exporting/resume`

## After Migration

Once the migration completes successfully:

1. **Stop Camunda**

2. **Update configuration** to use RDBMS for reads:
   ```yaml
   camunda:
     data:
       secondary-storage:
         type: rdbms
   ```

3. **Restart Camunda**

4. **(Optional) Disable Camunda exporter** to stop writing to ES:
   ```bash
   curl -X POST http://camunda:9600/actuator/exporters/camundaexporter/disable
   ```

## Troubleshooting

### Migration failed - exporting still paused

If the migration fails, the tool attempts to resume exporting automatically. If that fails, manually resume:

```bash
curl -X POST http://camunda:9600/actuator/exporting/resume
```

### Check migration job status

```bash
kubectl get jobs es-rdbms-migration
kubectl describe job es-rdbms-migration
```

### View logs

```bash
kubectl logs job/es-rdbms-migration
```

### Re-run migration

Delete the old job first:

```bash
kubectl delete job es-rdbms-migration
kubectl apply -f es-rdbms-migration-job.yaml
```

## Resource Requirements

Default resource requests/limits:
- Memory: 1Gi request / 4Gi limit
- CPU: 500m request / 2000m limit

For large migrations, you may need to increase these values in the Job manifest.

## Security Notes

- The ConfigMap contains credentials in plain text - for production, consider using Sealed Secrets, External Secrets Operator, or HashiCorp Vault
- The Job runs with default service account - consider using a dedicated service account with minimal permissions
- Ensure the ConfigMap is not committed to version control with real credentials
