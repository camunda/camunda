# Backup Store for Google Cloud Storage

Work in progress implementation of a Google Cloud Storage (GCS) backup store.

## Configuration

**Required**
- _bucketName_: Name of the bucket that will be used for storing backups. The bucket must already
exist.

**Optional**
- _basePath_: Prefix to use for all backup blobs. Useful for using one bucket across multiple Zeebe clusters.
