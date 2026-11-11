# Key index windows can use about 80 MB per partition

Status: open.

## Problem

Before the windows, a column family above `keyIndexMaxKeys` held no keys in the index. Now each
column family with a window policy can hold `keyIndexMaxKeys` keys all the time. Six column families have a policy: the four deadline column families, `ELEMENT_INSTANCE_PARENT_CHILD` and `NUMBER_OF_TAKEN_SEQUENCE_FLOWS`. All six can be at the cap. One key needs about 100 to
130 bytes. With the default cap of 100000 keys, this is about 13 MB per column family and about
80 MB per partition. The cap limits the memory, but it is new memory.

## Idea

Give some of this memory back by limiting the RocksDB memory, for example the block cache or the
memtables. This needs a measurement of the memory that the partition uses with and without the
windows.

## Open questions

- How much RocksDB memory can we remove without a loss in read speed?
- Is a lower `keyIndexMaxKeys` for some column families enough?
