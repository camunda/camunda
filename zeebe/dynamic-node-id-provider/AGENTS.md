# Dynamic Node ID Provider

## Purpose

This module provides dynamic node ID assignment for Zeebe brokers running in environments without
stable node identifiers (e.g., AWS ECS, serverless containers). It uses S3 as a coordination
backend for lease-based node ID allocation.

See [README.md](./README.md) for detailed implementation documentation.

## Module Structure

```
src/main/java/io/camunda/zeebe/dynamic/nodeid/
├── fs/                          # Filesystem/data directory management
│   ├── VersionedDirectoryLayout.java           # Versioned directory structure handling
│   ├── VersionedNodeIdBasedDataDirectoryProvider.java  # Main provider with versioning
│   ├── VersionedDataDirectoryGarbageCollector.java     # Cleanup of old versions
│   ├── DataDirectoryCopier.java                # Copies data between version directories
│   ├── DataDirectoryProvider.java              # Interface for directory providers
│   └── DirectoryInitializationInfo.java        # Metadata for initialized directories
├── repository/                  # Storage backends
│   ├── s3/
│   │   └── S3NodeIdRepository.java             # S3-based lease storage
│   ├── NodeIdRepository.java                   # Repository interface
│   └── Metadata.java                           # Lease metadata
├── NodeIdProvider.java          # Main interface for node ID acquisition
├── RepositoryNodeIdProvider.java # Implementation using repository backend
├── Lease.java                   # Lease representation and renewal logic
├── NodeInstance.java            # Node identity (id + version)
└── Version.java                 # Version number wrapper
```

## Key Concepts

### Lease-Based Node ID Assignment

- Nodes acquire leases for node IDs (0 to clusterSize-1) stored in S3
- Leases must be continuously renewed; failure to renew triggers shutdown
- Uses Compare-And-Swap (CAS) operations for consistency

### Lease Reacquisition on Restart

When a broker shuts down gracefully:
- The lease is released (S3 object becomes empty)
- On restart, the broker reacquires the same lease (same nodeId and taskId)
- Cluster remains functional with no data loss

### Versioned Data Directories

Each broker restart creates a new versioned directory:

```
data/
└── node-0/
    ├── v1/
    │   ├── directory-initialized.json
    │   └── ... (raft logs, snapshots, etc.)
    ├── v2/
    │   └── ...
    └── v3/
        └── ...
```

The `directory-initialized.json` file marks a directory as valid and refers to a serialized `DirectoryInitializationInfo`, which stores metadata about the initialized directory (such as node ID, version, and timestamps).

### Garbage Collection

`VersionedDataDirectoryGarbageCollector` removes old version directories:
- Keeps at least N valid (initialized) directories (configurable via `retentionCount`)
- Always deletes invalid directories (missing or corrupt initialization file)
- Runs after successful directory initialization and validation
