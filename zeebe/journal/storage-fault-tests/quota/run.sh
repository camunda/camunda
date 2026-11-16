#!/usr/bin/env bash
source /work/lazyfs-lib.sh; set +e
export LAZYFS_QUOTA_FILE=/tmp/quota-full
CP="/usr/local/camunda/lib/*"
JOPTS="--add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Dlog4j2.level=WARN"
mkdir -p /tmp/q && javac -nowarn -cp "$CP" -d /tmp/q /work/quota/QuotaTest.java || exit 1
for scenario in ${SCENARIOS:-insegment rollover}; do for end in ${ENDS:-close halt}; do
  echo "=================== $scenario / $end"
  rm -f "$LAZYFS_QUOTA_FILE"; rm -rf "$ROOT"; lfs_mount
  java $JOPTS -cp "/tmp/q:$CP" QuotaTest run "$MNT/j" "$scenario" "$end" "$LAZYFS_QUOTA_FILE" > /tmp/r.out 2>&1
  grep -E "^(QUOTA_ON|DISK|segments now|LAST_ACK|CLOSED|HALT)|Failed to acquire|OutOfDiskSpace:" /tmp/r.out | cut -c1-200 | sort -u | head -10
  echo "   fsyncs acknowledged without persisting: $(grep -c 'lazyfs.quota' /tmp/lazyfs.log)"
  # client cache lost (pod replaced): dirty pages reach LazyFS without fsync, then everything unsynced is dropped
  sync; echo "lazyfs::clear-cache" > "$FIFO"; sleep 0.5; lfs_umount
  rm -f "$LAZYFS_QUOTA_FILE"   # space available again
  lfs_mount
  java $JOPTS -cp "/tmp/q:$CP" QuotaTest verify "$MNT/j" 2>&1 | grep REOPEN | cut -c1-300
  lfs_umount
done; done
