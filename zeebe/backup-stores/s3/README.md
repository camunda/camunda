# Backup Store for S3

## Setup

This backup store implementation does not create a bucket itself, you must create one first[^bucket]
. The
bucket must only be used for backups of a single Zeebe cluster. Sharing a bucket across multiple
Zeebe clusters may result in corrupted backups.

[^bucket]: https://docs.aws.amazon.com/AmazonS3/latest/userguide/create-bucket-overview.html

## Compatibility

Advanced bucket features such as locking, versioning and encryption are not utilized.
We do not test compatibility with these features and recommend to not enable them if possible.

We test compatibility to [AWS S3] and [MinIO] only, but any S3 compatible object storage should be
usable.

[AWS S3]: https://docs.aws.amazon.com/s3/index.html
[MinIO]: https://min.io/product/s3-compatibility

## Configuration

**Required**

- `bucketName` Name of the bucket that will be used for storing backups. The bucket must already
  exist.

**Optional**

- `endpoint` URL to use when connecting to S3. If none is provided, the AWS SDK will use a default
  value.
- `region` Region to use when connecting to S3. If none is provided, the AWS SDK will try to
  determine an appropriate value. [^region]
- `credentials` A pair of `accessKey` and `secretKey`. If none are provided, the AWS SDK will try to
  determine an appropriate value. [^credentials]
- `waitForReplication` When enabled, a backup is only saved, and therefore only confirmed, once
  every object of the backup has been replicated by the bucket's own replication (S3 replication or
  MinIO bucket replication). See [Waiting for replication](#waiting-for-replication).
- `replicationPollInterval` How often to check the replication status of objects that are still
  pending. Defaults to 5 seconds.
- `replicationTimeout` How long to wait for all objects of a backup to be replicated before the
  backup fails. Defaults to 15 minutes.

[^region]: https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/region-selection.html#automatically-determine-the-aws-region-from-the-environment
[^credentials]: https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain

## Waiting for replication

With `waitForReplication`, the store observes the bucket's asynchronous replication to another site
before it reports a backup as saved, so a confirmed backup is restorable from the replica bucket:

1. After uploading the snapshot and segment objects, the store polls each object with a HEAD request
   until its `x-amz-replication-status` is `COMPLETED` (or `COMPLETE`).
2. Only then is the manifest marked as completed, so the replica site never sees a completed
   manifest without its contents. The store then waits for the manifest to be replicated too.

The backup fails, and its manifest is marked as failed, as soon as an object reports `FAILED` or no
replication status at all (replication is not set up, or no rule covers the object), or when not
every object was replicated within `replicationTimeout`. A failed backup is never confirmed. Only
the object-level permissions the store already needs are required: the replication status comes
from HEAD and GET requests on the backup objects.

While waiting is enabled, a completed manifest is reported by its own replication status: as in
progress while it is pending, as failed if its replication failed, and as completed once it is
replicated. Manifests without a replication status are reported unchanged.

## Known limitations

* There is no concept of backup rotation so backups will accumulate unless manually deleted.
* Individual files in a backup may not exceed 5GiB, otherwise the upload may fail.
* If the backup consists of more than 1000 files, deleting a backup may leave behind some objects.

## Internals

A backup consists of following objects prefixed by `partitionId/checkpointId/nodeId/` :

* `manifest.json`: A _manifest_ object, containing metadata and status information serialized as
  JSON
* `snapshots/*` Objects for _snapshot files_'
* `segments/*` Objects for _segment files_

