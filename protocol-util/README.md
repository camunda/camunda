# Zeebe Protocol Utilities

This module consists of shared utilities to manipulate and work with the Zeebe binary protocol.

## Type mappings

Included here are some type mapping utilities. They express the implicit mappings between certain
protocol properties in a more explicit way.

For one, by convention, every `ValueType` (outside of `SBE_UNKNOWN` and `NULL_VAL`) always has a
corresponding value type (i.e. an interface type extending `RecordValue`) and an intent type (i.e.
an enum extending `Intent`). This relationship is now encoded in `ValueTypeMapping`.

Second, every protocol value type (e.g. `Record`, `RecordValue` subtypes, etc.) has a concrete,
generated, immutable variant. This is expressed in `ProtocolTypeMapping`, and can be used, for
example, to simplify deserialization or copying of the interface types.
