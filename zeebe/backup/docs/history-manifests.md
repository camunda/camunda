# Event Plane history metadata

This is groundwork for the proposed [history-manifest ADR](../../../docs/adr/management/007-event-plane-history-manifests.md).
It does not activate Event Plane publishing.

Implemented:

- A versioned JSON manifest describes one physical tenant's history and each partition's source
  checkpoint. The fixture in `src/test/resources/history/restore.json` matches the Event Plane contract.
- S3, GCS, Azure and filesystem backup stores persist manifests at `histories/<uuid>.json` beneath
  their configured prefix, using ordinary writes. Backup deletion leaves these objects intact.
- Backup descriptors preserve an optional history UUID. Legacy descriptors retain unknown ancestry.
- `PartitionRestoreService` returns the restored checkpoint's index, position and source history.
  A chain with unknown provenance remains unknown; conflicting known histories fail restoration.
- `HistoryManifestPublication` requires an archive-preparation callback to succeed before writing
  the manifest. Callers must persist the operation identity and reuse identical metadata on retry.
- Online restore changes the existing numeric incarnation before any partition resumes processing.

Still required before runtime activation:

- Persist and distribute one history UUID, timestamp and partition map for creation, purge, online
  restore and both offline restore modes. Install them before processing, including after a crash.
- Capture that UUID when producing backup descriptors. The optional field is preserved today, but
  normal backup production does not yet populate it.
- Implement inherited-journal preparation, the replicated upload floor and a publisher that starts
  after the prepared checkpoint. There is no production preparation callback or publication caller.
- Supply the archive location independently of the Event Plane. The storage adapters use the
  existing backup store; configuring shared archive storage and protecting shared segments remain
  separate work. Native backup completion is not evidence that the Event Plane prefix is archived.
- Wire restore reports into tenant-wide preparation and activation; define migration for backups
  without provenance. Existing restore callers may ignore the new report while publishing is off.

The manifest PUT must complete before processing is allowed. A failed or uncertain response is
retried with the same bytes; it must not generate another history. No conditional writes are used
by the history-manifest protocol. Existing backup storage protocols are unchanged.
