# Backup Store for S3

## Setup

This backup store implementation does not create a bucket itself, you must create one first[^bucket]
. The
bucket must only be used for backups of a single Zeebe cluster. Sharing a bucket across multiple
Zeebe clusters may result in corrupted backups.

[^bucket]: https://docs.aws.amazon.com/AmazonS3/latest/userguide/create-bucket-overview.html

## Compatability

Advanced bucket features such as locking, versioning and encryption are not utilized.
We do not test compatability with these features and recommend to not enable them if possible.

We test compatability to [AWS S3] and [MinIO] only, but any S3 compatible object storage should be
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

[^region]: https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/region-selection.html#automatically-determine-the-aws-region-from-the-environment
[^credentials]: https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain

## Known limitations

* There is no concept of backup rotation so backups will accumulate unless manually deleted.
* Individual files in a backup may not exceed 5GiB, otherwise the upload may fail.
* If the backup consists of more than 1000 files, deleting a backup may leave behind some objects.

## Internals

A backup consists of 2 objects used for managing the backup and the objects
containing the backup data itself.
All object keys are prefixed by `partitionId/checkpointId/nodeId` which uniquely identifies a
backup:

* `metadata.json`: A _metadata_ object, containing metadata serialized as JSON
* `status.json`: A _status_ object, containing the current status serialized as JSON
* `snapshots/*` Objects for _snapshot files_, additionally prefixed with 'snapshot'
* `segments/*` Objects for _segment files_, additionally prefixed with 'segments'

