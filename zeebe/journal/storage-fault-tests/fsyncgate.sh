#!/usr/bin/env bash
# Transient I/O error under the 8.8.41 journal on ext4 (device-mapper linear/error).
# usage: fsyncgate.sh <after: dropcache|umount>
set -u
AFTER=${1:-dropcache}
mkdir -p /work/fsyncgate-logs
CP="${CAMUNDA_LIB:-/usr/local/camunda/lib}/*"
JOPTS="--add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Dlog4j2.configurationFile=/work/journal-log4j2.xml"
command -v dmsetup >/dev/null || { apt-get update -qq >/dev/null 2>&1; apt-get install -y -qq dmsetup e2fsprogs >/dev/null 2>&1; }
mkdir -p /tmp/h && javac -nowarn -cp "$CP" -d /tmp/h /work/harness/JournalCrash.java /work/harness/FsyncGate.java || exit 1
CP="/tmp/h:$CP"

IMG=/var/fg.img; MNT=/mnt/fg
dmsetup remove fg 2>/dev/null; for l in $(losetup -j $IMG 2>/dev/null | cut -d: -f1); do losetup -d $l; done
dd if=/dev/zero of=$IMG bs=1M count=256 status=none
LOOP=$(losetup -f --show $IMG)
SECTORS=$(blockdev --getsz $LOOP)
echo "0 $SECTORS linear $LOOP 0" | dmsetup create fg && dmsetup mknodes fg
mkfs.ext4 -q -F /dev/mapper/fg
mkdir -p $MNT && mount -o errors=continue,commit=60 /dev/mapper/fg $MNT || exit 1
mkdir -p $MNT/w

java $JOPTS -cp "$CP" FsyncGate run $MNT/w/j > /tmp/fg.out 2>/tmp/fg.err &
PID=$!
until grep -q '^SEGMENT' /tmp/fg.out; do sleep 0.1; kill -0 $PID || { cat /tmp/fg.err; exit 1; }; done
SEG=$(grep '^SEGMENT' /tmp/fg.out | cut -d' ' -f2)
# physical extents of the segment file (ext4 blocks of 4 KiB = 8 sectors) -> error target for them
BS=$(stat -f -c %S $MNT); echo "fs block size $BS"; filefrag -e -b$BS "$SEG" | sed "s/^/   filefrag: /"
K=$((BS/512)); P=0; TABLE=""
while read -r ps pe; do
  st=$((ps*K)); en=$(((pe+1)*K))
  [ $st -gt $P ] && TABLE+="$P $((st-P)) linear $LOOP $P"$'\n'
  TABLE+="$st $((en-st)) error"$'\n'; P=$en
done < <(filefrag -e -b$BS "$SEG" | sed -nE 's/^ *[0-9]+: +[0-9]+\.\. *[0-9]+: +([0-9]+)\.\. *([0-9]+):.*/\1 \2/p')
[ $P -lt $SECTORS ] && TABLE+="$P $((SECTORS-P)) linear $LOOP $P"
echo "segment $SEG mapped to error:"; echo "$TABLE" | grep error | sed 's/^/   /'
echo "$TABLE" | dmsetup load fg && dmsetup suspend fg && dmsetup resume fg
touch $MNT/w/go-error
until grep -q '^WINDOW_DONE' /tmp/fg.out; do sleep 0.1; done
echo "0 $SECTORS linear $LOOP 0" | dmsetup load fg && dmsetup suspend fg && dmsetup resume fg
touch $MNT/w/go-ok
until grep -q '^END' /tmp/fg.out; do sleep 0.1; kill -0 $PID || break; done
wait $PID
cp /tmp/fg.out /work/fsyncgate-logs/run.log; dmesg | grep -iE "I/O error|EXT4-fs" > /work/fsyncgate-logs/kernel.log
echo "--- journal process:"; grep -vE '^ACK' /tmp/fg.out | sed 's/^/   /'; echo "   acks: $(grep '^ACK' /tmp/fg.out | tr '\n' ' ' | cut -c1-400)"
grep -m3 -E 'Exception|Error' /tmp/fg.err | sed 's/^/   err: /'
echo "--- kernel:"; dmesg 2>/dev/null | grep -iE 'I/O error|EXT4-fs.*(error|warning)|lost async page write' | tail -4 | sed 's/^/   /'

if [ "$AFTER" = umount ]; then umount $MNT; mount -o errors=continue /dev/mapper/fg $MNT
else sync; echo 3 > /proc/sys/vm/drop_caches; fi
echo "--- reopen after $AFTER:"
java $JOPTS -cp "$CP" JournalCrash verify $MNT/w/j lazy > /work/fsyncgate-logs/reopen.log 2>&1; grep -E '^(OPEN_|READ_FAIL|INDEX_GAP)' /work/fsyncgate-logs/reopen.log | sed 's/^/   /'
umount $MNT; dmsetup remove fg; losetup -d $LOOP
