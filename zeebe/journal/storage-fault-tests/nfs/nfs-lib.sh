#!/usr/bin/env bash
# NFSv3 export of a LazyFS mount on localhost. Server crash = forced client unmount + LazyFS drops
# everything the NFS server never synced.
source /work/lazyfs-lib.sh; set +e
NFSMNT=/mnt/nfs
nfs_start() {   # $1 = sync|async
  lfs_mount || return 1
  mkdir -p "$NFSMNT" /run/rpcbind; rpcbind -w 2>/dev/null || rpcbind
  mountpoint -q /proc/fs/nfsd || mount -t nfsd nfsd /proc/fs/nfsd
  echo "$MNT 127.0.0.1(rw,$1,no_subtree_check,no_root_squash,fsid=77)" > /etc/exports
  exportfs -ra 2>&1 | grep -v '^$'; rpc.mountd; rpc.nfsd -N 4 4 2>/dev/null || rpc.nfsd 4
  mount -t nfs -o vers=3,nolock,soft,timeo=10,retrans=1 127.0.0.1:"$MNT" "$NFSMNT" || { echo "NFS mount failed"; return 1; }
}
nfs_server_crash() {   # client gone + server loses what it never synced
  umount -f -l "$NFSMNT" 2>/dev/null
  rpc.nfsd 0; exportfs -ua; pkill rpc.mountd
  # no sync: the client node is gone with its dirty pages
  echo "lazyfs::clear-cache" > "$FIFO"; sleep 0.5
  lfs_umount
}
nfs_stop() { umount -f -l "$NFSMNT" 2>/dev/null; rpc.nfsd 0; exportfs -ua; pkill rpc.mountd; lfs_umount; }
