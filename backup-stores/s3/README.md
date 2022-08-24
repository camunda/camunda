# Backup Store for S3

## Known limitations

* Individual files in a backup may not exceed 5GiB, otherwise the upload may fail
* If the backup consists of more than 1000 files, deleting a backup may leave behind some objects.

