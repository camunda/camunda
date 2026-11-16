# Adds a "quota exhausted" model to LazyFS's fsync: if the file named by $LAZYFS_QUOTA_FILE exists
# and contains a regex matching the path, fsync acknowledges without persisting (data stays unsynced
# in LazyFS's cache and is lost on clear-cache), like a write-back client whose commit to the
# backend fails silently when the quota is exceeded. All other files sync normally.
import re, sys
p = sys.argv[1]
s = open(p).read()
sig = 'int LazyFS::lfs_fsync (const char* path, int isdatasync, struct fuse_file_info* fi) {'
assert sig in s, 'fsync signature not found'
inject = sig + r'''
    {
        const char* quota_file = std::getenv ("LAZYFS_QUOTA_FILE");
        if (quota_file != nullptr) {
            std::ifstream in (quota_file);
            std::string pattern;
            if (in && std::getline (in, pattern) && !pattern.empty () &&
                std::regex_search (std::string (path), std::regex (pattern))) {
                spdlog::info ("[lazyfs.quota]: fsync(path={}) acknowledged without persisting", path);
                return 0;
            }
        }
    }
'''
s = s.replace(sig, inject, 1)
s = '#include <fstream>\n#include <regex>\n#include <cstdlib>\n' + s
open(p, 'w').write(s)
print('patched')
